#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
litematic_to_blueprint.py
=========================
把一个 Litematica 投影文件 (.litematic) 解析成「多方块结构蓝图」：
  - 终端打印 ASCII 分层俯视图 + 图例 + 每个非空气方块的(坐标, 方块id, 状态属性)
  - 可选输出 JSON 蓝图文件，供 mod 后续直接读取

不依赖任何第三方库，仅用标准库。

格式依据（已对照 sakura-ryoko/litematica @ 26.2 源码确认）：
  .litematic = GZip 压缩的 NBT，根 Compound 含：
    MinecraftDataVersion : int
    Version              : int   (v6 = 6)
    SubVersion           : int
    Metadata             : Compound(Name, EnclosingSize{x,y,z}, RegionCount, ...)
    Regions              : Compound，键=子区域名，值=子区域 Compound：
        Size              : {x,y,z}           区域尺寸
        Position          : {x,y,z}           区域在整体中的偏移
        BlockStatePalette : List<Compound>    每项 {Name:str, Properties:Compound(str->str)}
        BlockStates        : long[]           调色板下标的位打包数组
        TileEntities / Entities / Pending*Ticks : （本脚本忽略）
  BlockStates 位打包（LitematicaBitArray，小端、可跨 long）：
        bits = max(2, (palette_size - 1).bit_length())
        线性索引 i = y*(sx*sz) + z*sx + x   （x 最快，其次 z，最后 y）
        getAt(i): 从 bit 偏移 i*bits 起读 bits 位（无符号右移语义）

用法：
    python litematic_to_blueprint.py <输入.litematic> [-o 输出.json] [--no-ascii] [--selftest]
"""
import sys
import os
import gzip
import struct
import json
import argparse


# --------------------------------------------------------------------------
# 极简 NBT 解析器（仅实现本脚本所需标签）
# --------------------------------------------------------------------------
class NBT:
    TAG_END = 0
    TAG_BYTE = 1
    TAG_SHORT = 2
    TAG_INT = 3
    TAG_LONG = 4
    TAG_FLOAT = 5
    TAG_DOUBLE = 6
    TAG_BYTE_ARRAY = 7
    TAG_STRING = 8
    TAG_LIST = 9
    TAG_COMPOUND = 10
    TAG_INT_ARRAY = 11
    TAG_LONG_ARRAY = 12

    def __init__(self, data: bytes):
        self.buf = data
        self.pos = 0

    def _read(self, n):
        b = self.buf[self.pos:self.pos + n]
        if len(b) < n:
            raise EOFError(f"NBT 读取越界：需要 {n} 字节，仅剩 {len(b)}")
        self.pos += n
        return b

    def u8(self):
        return self._read(1)[0]

    def u16(self):
        return struct.unpack('>H', self._read(2))[0]

    def i32(self):
        return struct.unpack('>i', self._read(4))[0]

    def i64(self):
        return struct.unpack('>q', self._read(8))[0]

    def string(self):
        ln = self.u16()
        return self._read(ln).decode('utf-8', errors='replace')

    def parse(self):
        t = self.u8()
        name = self.string()
        if t != self.TAG_COMPOUND:
            raise ValueError(f"根标签类型为 {t}，期望 COMPOUND(10)")
        return self._read_compound()

    def _read_compound(self):
        out = {}
        while True:
            t = self.u8()
            if t == self.TAG_END:
                break
            key = self.string()
            out[key] = self._read_value(t)
        return out

    def _read_value(self, t):
        if t == self.TAG_COMPOUND:
            return self._read_compound()
        if t == self.TAG_BYTE:
            return self.u8()
        if t == self.TAG_SHORT:
            return struct.unpack('>h', self._read(2))[0]
        if t == self.TAG_INT:
            return self.i32()
        if t == self.TAG_LONG:
            return self.i64() & 0xFFFFFFFFFFFFFFFF  # 转无符号 64 位，匹配 Java >>> 语义
        if t == self.TAG_FLOAT:
            return struct.unpack('>f', self._read(4))[0]
        if t == self.TAG_DOUBLE:
            return struct.unpack('>d', self._read(8))[0]
        if t == self.TAG_BYTE_ARRAY:
            n = self.i32()
            return self._read(n)
        if t == self.TAG_STRING:
            return self.string()
        if t == self.TAG_LIST:
            et = self.u8()
            n = self.i32()
            if et == self.TAG_COMPOUND:
                # 列表中的 compound 元素不带前导 TAG 字节，直接读 body
                return [self._read_compound() for _ in range(n)]
            return [self._read_value(et) for _ in range(n)]
        if t == self.TAG_INT_ARRAY:
            n = self.i32()
            return list(struct.unpack('>%di' % n, self._read(4 * n)))
        if t == self.TAG_LONG_ARRAY:
            n = self.i32()
            arr = struct.unpack('>%dq' % n, self._read(8 * n))
            return [x & 0xFFFFFFFFFFFFFFFF for x in arr]  # 无符号
        raise ValueError(f"未知 NBT 标签类型 {t}")


# --------------------------------------------------------------------------
# .litematic 解码核心
# --------------------------------------------------------------------------
AIR_IDS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


# NBT 写入辅助类型（仅自检构造器使用）
class _LongArray:
    def __init__(self, vals):
        self.vals = vals


class _IntArray:
    def __init__(self, vals):
        self.vals = vals


def bits_for_palette(palette_size: int) -> int:
    v = palette_size - 1
    return max(2, v.bit_length())


def get_at(long_array, index: int, bits: int) -> int:
    mask = (1 << bits) - 1
    start_offset = index * bits
    start_arr = start_offset >> 6
    end_arr = ((index + 1) * bits - 1) >> 6
    start_bit = start_offset & 0x3F
    a = long_array[start_arr]
    if start_arr == end_arr:
        return (a >> start_bit) & mask
    b = long_array[end_arr]
    end_offset = 64 - start_bit
    return ((a >> start_bit) | (b << end_offset)) & mask


def decode_region(region: dict):
    """返回该区域内所有方块（含空气）的 (gx,gy,gz, palette_index) 列表。"""
    size = region['Size']
    sx, sy, sz = int(size['x']), int(size['y']), int(size['z'])
    pos = region.get('Position', {'x': 0, 'y': 0, 'z': 0})
    px, py, pz = int(pos['x']), int(pos['y']), int(pos['z'])
    palette = region['BlockStatePalette']
    longs = region['BlockStates']
    bits = bits_for_palette(len(palette))
    total = sx * sy * sz
    blocks = []
    for i in range(total):
        x = i % sx
        z = (i // sx) % sz
        y = i // (sx * sz)
        pal_idx = get_at(longs, i, bits)
        blocks.append((px + x, py + y, pz + z, pal_idx))
    return blocks, palette, (sx, sy, sz), (px, py, pz)


def block_id_of(palette_entry) -> str:
    return palette_entry.get('Name', 'minecraft:air')


def block_props_of(palette_entry) -> dict:
    props = palette_entry.get('Properties', {})
    if isinstance(props, dict):
        return {k: v for k, v in props.items()}
    return {}


def analyze(root: dict):
    """解析整个 .litematic，返回结构化蓝图。"""
    meta = root.get('Metadata', {})
    name = meta.get('Name', '')
    mc_ver = root.get('MinecraftDataVersion')
    ver = root.get('Version')
    regions_tag = root.get('Regions', {})
    if not isinstance(regions_tag, dict):
        raise ValueError("Regions 标签不是 Compound")

    parts = []          # 非空气方块：{pos:[x,y,z], block, state:{...}}
    bounds = None
    region_summ = []
    for rname, region in regions_tag.items():
        blocks, palette, (sx, sy, sz), (px, py, pz) = decode_region(region)
        cnt = 0
        for (gx, gy, gz, pidx) in blocks:
            if pidx < 0 or pidx >= len(palette):
                continue
            entry = palette[pidx]
            bid = block_id_of(entry)
            if bid in AIR_IDS:
                continue
            props = block_props_of(entry)
            parts.append({'pos': [gx, gy, gz], 'block': bid, 'state': props})
            cnt += 1
            if bounds is None:
                bounds = [gx, gy, gz, gx, gy, gz]
            else:
                bounds[0] = min(bounds[0], gx); bounds[1] = min(bounds[1], gy); bounds[2] = min(bounds[2], gz)
                bounds[3] = max(bounds[3], gx); bounds[4] = max(bounds[4], gy); bounds[5] = max(bounds[5], gz)
        region_summ.append({'name': rname, 'size': [sx, sy, sz], 'position': [px, py, pz], 'blocks': cnt})

    return {
        'name': name,
        'mc_data_version': mc_ver,
        'version': ver,
        'regions': region_summ,
        'bounds': bounds,
        'parts': parts,
    }


# --------------------------------------------------------------------------
# 输出渲染
# --------------------------------------------------------------------------
def _next_symbol(idx: int) -> str:
    if idx < 26:
        return chr(ord('A') + idx)
    idx -= 26
    if idx < 26:
        return chr(ord('a') + idx)
    idx -= 26
    if idx < 10:
        return str(idx)
    return '#'


def render_ascii(blueprint: dict):
    parts = blueprint['parts']
    if not parts:
        print("(结构里没有非空气方块)")
        return
    # 符号表：方块 id -> 字母
    sym_map = {}
    order = []
    for p in parts:
        bid = p['block']
        if bid not in sym_map:
            sym_map[bid] = _next_symbol(len(order))
            order.append(bid)
    b = blueprint['bounds']
    minx, miny, minz, maxx, maxy, maxz = b
    w = maxx - minx + 1
    h = maxz - minz + 1
    # 坐标 -> 符号
    grid = {}
    for p in parts:
        x, y, z = p['pos']
        grid[(x, y, z)] = sym_map[p['block']]

    print(f"\n=== ASCII 俯视（合并所有区域，'.' = 空气）===")
    print(f"整体包围盒 X[{minx}..{maxx}] Y[{miny}..{maxy}] Z[{minz}..{maxz}]")
    for y in range(maxy, miny - 1, -1):
        print(f"\n--- Y={y} ---")
        for z in range(minz, maxz + 1):
            row = ''.join(grid.get((x, y, z), '.') for x in range(minx, maxx + 1))
            print(f"z={z:>4} |{row}|")
    print()
    print("=== 图例 ===")
    for bid in order:
        print(f"  {sym_map[bid]} = {bid}")


def render_parts(blueprint: dict):
    print("\n=== 方块清单（全局相对坐标 / 方块 id / 状态属性）===")
    if blueprint['bounds']:
        print(f"包围盒={blueprint['bounds']}（x,y,z）")
    for i, p in enumerate(blueprint['parts']):
        st = p['state']
        if st:
            stxt = " ".join(f"{k}={v}" for k, v in st.items())
            print(f"  [{i:>3}] {p['pos']}  {p['block']}  {stxt}")
        else:
            print(f"  [{i:>3}] {p['pos']}  {p['block']}")


# --------------------------------------------------------------------------
# 自检：不依赖真实文件，验证位解码与位宽公式
# --------------------------------------------------------------------------
def selftest():
    ok = True

    # 1) bits_for_palette
    expect = {1: 2, 2: 2, 3: 2, 4: 2, 5: 3, 8: 3, 9: 4, 16: 4, 17: 5}
    for n, e in expect.items():
        g = bits_for_palette(n)
        if g != e:
            print(f"[FAIL] bits_for_palette({n})={g} 期望 {e}")
            ok = False

    # 2) get_at 单 long 内：bits=2，存 [1,2,3,0] 到索引 0..3（占 bit0-7）
    #    long = 0b 00 11 10 01 = 0x39? 低2位=索引0=1, 次低2位=索引1=2, 再=索引2=3, 高2位=索引3=0
    long0 = (0 << 6) | (3 << 4) | (2 << 2) | (1 << 0)
    arr = [long0 & 0xFFFFFFFFFFFFFFFF]
    got = [get_at(arr, i, 2) for i in range(4)]
    if got != [1, 2, 3, 0]:
        print(f"[FAIL] get_at 单long 内: {got} 期望 [1,2,3,0]")
        ok = False

    # 3) get_at 跨 long：bits=4，索引15 落在 long0 的 bit60-63，索引16 落在 long1 的 bit0-3
    #    long0 的 bit60-63 = 值 9；long1 的 bit0-3 = 值 7
    long0 = 9 << 60
    long1 = 7
    arr2 = [long0 & 0xFFFFFFFFFFFFFFFF, long1 & 0xFFFFFFFFFFFFFFFF]
    if get_at(arr2, 15, 4) != 9:
        print(f"[FAIL] get_at 跨long 索引15 = {get_at(arr2,15,4)} 期望 9")
        ok = False
    if get_at(arr2, 16, 4) != 7:
        print(f"[FAIL] get_at 跨long 索引16 = {get_at(arr2,16,4)} 期望 7")
        ok = False

    # 4) 端到端：用 NBT 写一个最小 .litematic（1x1x2：底部 oak_stairs 朝北下半，顶部空气）并解析
    schematic = build_minimal_litematic()
    raw = gzip.compress(schematic)
    data = gzip.decompress(raw)
    root = NBT(data).parse()
    bp = analyze(root)
    # 期望：1 个非空气方块（oak_stairs），位置取决于 region Position
    non_air = [p for p in bp['parts'] if p['block'] != 'minecraft:air']
    if len(non_air) != 1 or non_air[0]['block'] != 'minecraft:oak_stairs':
        print(f"[FAIL] 端到端：非空气方块 = {non_air}")
        ok = False
    else:
        st = non_air[0]['state']
        if st.get('facing') != 'north' or st.get('half') != 'bottom' or st.get('shape') != 'straight':
            print(f"[FAIL] 端到端：状态属性错误 {st}")
            ok = False

    if ok:
        print("[SELFTEST PASS] 位宽公式 / get_at(单long内、跨long) / 端到端 NBT 解析 全部通过")
    else:
        print("[SELFTEST FAILED]")
        sys.exit(1)


def build_minimal_litematic() -> bytes:
    """手工构造一个最小 .litematic（NBT）用于自检：
       区域尺寸 1x2x1，Position=(0,0,0)
       palette: [0]=minecraft:air, [1]=minecraft:oak_stairs(facing=north,half=bottom,shape=straight)
       BlockStates: 索引0(y=0)=1, 索引1(y=1)=0  -> bits=2
    """
    # 构造 BlockStatePalette list
    palette = [
        {'Name': 'minecraft:air', 'Properties': {}},
        {'Name': 'minecraft:oak_stairs', 'Properties': {'facing': 'north', 'half': 'bottom', 'shape': 'straight'}},
    ]

    def write_compound(d: dict) -> bytes:
        out = bytearray()
        for k, v in d.items():
            out += _write_value(v, k)
        out += bytes([0])  # TAG_End
        return bytes(out)

    def _write_value(v, name=None) -> bytes:
        if isinstance(v, dict):
            # compound
            body = write_compound(v)
            return bytes([NBT.TAG_COMPOUND]) + _write_str(name) + body
        if isinstance(v, list):
            # 本脚本只用「元素为 Compound」的列表（如 BlockStatePalette）
            out = bytearray()
            out += bytes([NBT.TAG_LIST])
            out += _write_str(name)            # 字段名不能漏
            out += bytes([NBT.TAG_COMPOUND])  # 元素类型
            out += struct.pack('>i', len(v))
            for item in v:
                out += write_compound(item)
            return bytes(out)
        if isinstance(v, str):
            return bytes([NBT.TAG_STRING]) + _write_str(name) + _write_str(v)
        if isinstance(v, int):
            return bytes([NBT.TAG_INT]) + _write_str(name) + struct.pack('>i', v)
        if isinstance(v, _LongArray):
            n = len(v.vals)
            return bytes([NBT.TAG_LONG_ARRAY]) + _write_str(name) + struct.pack('>i', n) + struct.pack('>%dq' % n, *v.vals)
        if isinstance(v, _IntArray):
            n = len(v.vals)
            return bytes([NBT.TAG_INT_ARRAY]) + _write_str(name) + struct.pack('>i', n) + struct.pack('>%di' % n, *v.vals)
        if isinstance(v, bytes):
            return bytes([NBT.TAG_BYTE_ARRAY]) + _write_str(name) + struct.pack('>i', len(v)) + v
        raise ValueError(f"unsupported self-test value {type(v)}")

    def _write_str(s: str) -> bytes:
        b = s.encode('utf-8')
        return struct.pack('>H', len(b)) + b

    # BlockStates long[]：bits=2，total=2 个方块
    # 索引0 -> palette 1 ; 索引1 -> palette 0
    bits = 2
    longs = [0] * 1
    # 直接用与 get_at 相同的打包写入
    def set_at(long_array, index, value, bits):
        mask = (1 << bits) - 1
        off = index * bits
        ai = off >> 6
        bo = off & 0x3F
        long_array[ai] = (long_array[ai] & ~(mask << bo) | ((value & mask) << bo)) & 0xFFFFFFFFFFFFFFFF
    set_at(longs, 0, 1, bits)
    set_at(longs, 1, 0, bits)
    long_arr_bytes = struct.pack('>%dq' % len(longs), *longs)

    # 组装 region
    region = {
        'Size': {'x': 1, 'y': 2, 'z': 1},
        'Position': {'x': 0, 'y': 0, 'z': 0},
        'BlockStatePalette': palette,
        'BlockStates': _LongArray(longs),
    }
    regions = {'region0': region}
    metadata = {'Name': 'selftest', 'RegionCount': 1,
                'EnclosingSize': {'x': 1, 'y': 2, 'z': 1}}
    root = {
        'MinecraftDataVersion': 4080,
        'Version': 6,
        'SubVersion': 0,
        'Metadata': metadata,
        'Regions': regions,
    }
    body = write_compound(root)
    return bytes([NBT.TAG_COMPOUND]) + _write_str('') + body


# --------------------------------------------------------------------------
# 主入口
# --------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description='解析 Litematica .litematic 为多方块结构蓝图')
    ap.add_argument('input', nargs='?', help='输入的 .litematic 文件')
    ap.add_argument('-o', '--out', help='把 JSON 蓝图写到该路径')
    ap.add_argument('--no-ascii', action='store_true', help='不打印 ASCII 图')
    ap.add_argument('--selftest', action='store_true', help='运行内置自检后退出')
    args = ap.parse_args()

    if args.selftest:
        selftest()
        return

    if not args.input:
        ap.error('缺少 input 文件（或用 --selftest 自检）')

    if not os.path.isfile(args.input):
        print(f"找不到文件：{args.input}", file=sys.stderr)
        sys.exit(1)

    with open(args.input, 'rb') as f:
        raw = f.read()
    try:
        data = gzip.decompress(raw)
    except OSError:
        data = raw  # 容错：部分导出可能不是 gz（极少见）
    root = NBT(data).parse()
    bp = analyze(root)

    print(f"文件: {args.input}")
    print(f"投影名称: {bp['name']}")
    print(f"Litematica 版本: {bp['version']}  MC DataVersion: {bp['mc_data_version']}")
    print(f"区域数: {len(bp['regions'])}")
    for r in bp['regions']:
        print(f"  - {r['name']}: 尺寸={r['size']} 偏移={r['position']} 非空气方块={r['blocks']}")

    if not args.no_ascii:
        render_ascii(bp)
    render_parts(bp)

    if args.out:
        with open(args.out, 'w', encoding='utf-8') as f:
            json.dump(bp, f, ensure_ascii=False, indent=2)
        print(f"\nJSON 蓝图已写入: {args.out}")


if __name__ == '__main__':
    main()
