#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CurseForge 文件上传脚本（stdlib only，无需第三方依赖）。

用法:
  set CF_PROJECT_ID=xxxx
  set CF_TOKEN=yyyy          # 从 curseforge.com/account/api-tokens 生成
  # 沙箱 MITM 代理下若证书校验失败，加: set CF_INSECURE=1
  python cf_upload.py

行为:
  1) GET /api/game/versions + /api/game/version-types，把版本名解析成数字 ID（避免名称歧义）
  2) 对 build/libs 下 4 个 jar 逐个 POST /api/projects/{id}/upload-file
  每个文件标记 releaseType=release、changelog 取自本目录 curseforge-changelog.md
  （fallback 到 build/libs/release-notes-1.2.1-121x.md），relations 把 fabric-api 标 requiredDependency
"""
import os
import sys
import json
import uuid
import ssl
import urllib.request
import urllib.error

BASE = "https://minecraft.curseforge.com/api"

PROJECT_ID = 1631427
TOKEN = 1197d1c7-149f-4914-903e-913c34e83529
if not PROJECT_ID or not TOKEN:
    sys.exit("ERROR: 必须设置环境变量 CF_PROJECT_ID 与 CF_TOKEN")

if os.environ.get("CF_INSECURE") == "1":
    ssl._create_default_https_context = ssl._create_unverified_context

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIBS = os.path.join(ROOT, "build", "libs")
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CHANGELOG = os.path.join(SCRIPT_DIR, "curseforge-changelog.md")
if not os.path.exists(CHANGELOG):
    CHANGELOG = os.path.join(LIBS, "release-notes-1.2.1-121x.md")

# jar 文件名 -> 需要标记的 game version 名（运行时解析成数字 ID）
FILES = {
    "laowu_meme-1.2.1+1.21.0-1.21.1.jar": ["1.21.0", "1.21.1", "Fabric", "Java 21"],
    "laowu_meme-1.2.1+1.21.11.jar": ["1.21.11", "Fabric", "Java 21"],
    "laowu_meme-1.2.1+26.1.2.jar": ["26.1.2", "Fabric", "Java 21"],
    "laowu_meme-1.2.1+26.2.jar": ["26.2", "Fabric", "Java 21"],
}


def api_get(path):
    req = urllib.request.Request(
        BASE + path,
        headers={"X-Api-Token": TOKEN, "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())


def resolve_version_ids(names):
    vers = api_get("/game/versions")
    types = api_get("/game/version-types")
    type_by_id = {t["id"]: t for t in types}
    # 建立 (typeName, name) 与 (typeSlug, name) 两种索引
    by_key = {}
    for v in vers:
        t = type_by_id.get(v.get("gameVersionTypeID"), {})
        tname = t.get("name")
        tslug = t.get("slug")
        if tname:
            by_key[(tname, v["name"])] = v["id"]
        if tslug:
            by_key[(tslug, v["name"])] = v["id"]
    ids = []
    missing = []
    for n in names:
        found = None
        for key in by_key:
            if key[1] == n:
                found = by_key[key]
                break
        if found:
            ids.append(found)
        else:
            missing.append(n)
    if missing:
        print("  [WARN] 未能解析版本数字 ID（请确认 CurseForge 是否存在该版本）:", missing)
    return ids


def upload(jar_path, version_ids, display_name):
    boundary = "----WB" + uuid.uuid4().hex
    meta = {
        "displayName": display_name,
        "releaseType": "release",
        "changelog": open(CHANGELOG, encoding="utf-8").read(),
        "changelogType": "markdown",
        "gameVersions": version_ids,
        "relations": {
            "projects": [
                {"slug": "fabric-api", "type": "requiredDependency"}
            ]
        },
    }
    body = b""
    # metadata 字段
    body += ("--" + boundary + "\r\n").encode()
    body += b'Content-Disposition: form-data; name="metadata"\r\n'
    body += b"Content-Type: application/json\r\n\r\n"
    body += json.dumps(meta).encode("utf-8") + b"\r\n"
    # file 字段
    fname = os.path.basename(jar_path)
    body += ("--" + boundary + "\r\n").encode()
    body += ('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % fname).encode()
    body += b"Content-Type: application/java-archive\r\n\r\n"
    with open(jar_path, "rb") as f:
        body += f.read()
    body += b"\r\n"
    body += ("--" + boundary + "--\r\n").encode()

    url = BASE + "/projects/%s/upload-file" % PROJECT_ID
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("X-Api-Token", TOKEN)
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            resp = json.loads(r.read().decode())
            print("  [OK] %s -> file id %s" % (fname, resp.get("id")))
            return True
    except urllib.error.HTTPError as e:
        print("  [FAIL] %s -> HTTP %s: %s" % (fname, e.code, e.read().decode()[:600]))
        return False


def main():
    if not os.path.exists(CHANGELOG):
        sys.exit("ERROR: 找不到 changelog 文件: %s" % CHANGELOG)
    print("changelog: %s" % CHANGELOG)
    print("解析 game version 数字 ID ...")
    # 收集所有需要的版本名，一次性解析
    all_names = sorted({n for v in FILES.values() for n in v})
    id_map = {}
    for n in all_names:
        ids = resolve_version_ids([n])
        id_map[n] = ids[0] if ids else None
    print("  版本 ID 映射:", {k: id_map[k] for k in id_map})
    ok = 0
    for jar, names in FILES.items():
        jar_path = os.path.join(LIBS, jar)
        if not os.path.exists(jar_path):
            print("[SKIP] 找不到 jar: %s" % jar_path)
            continue
        vids = [id_map[n] for n in names if id_map.get(n)]
        if not vids:
            print("[SKIP] %s 无任何可解析版本 ID，跳过" % jar)
            continue
        disp = jar[:-4]  # 去掉 .jar 作 displayName
        print("上传 %s (versions=%s) ..." % (jar, vids))
        if upload(jar_path, vids, disp):
            ok += 1
    print("\n完成：成功 %d / 共 %d 个 jar" % (ok, len(FILES)))


if __name__ == "__main__":
    main()
