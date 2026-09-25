#!/usr/bin/env python3
import hashlib
import sys
from pathlib import Path

STOCK_SHA256 = "407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32"
PREVIOUS_SHA256 = "ef523f9d57ebe5c3af2ec39747b07ab6d70bd69165461e09ddc0fc8f13f5d563"
PATCHED_SHA256 = "7cb2123d0b5d5cfd9ae63699c9624ebe306392b88f642ec609fdb5fc2247e30f"
EXPECTED_SIZE = 11577024

# Reproduces the user-validated HybridMount native freshness patch, then adds one
# decompile-grounded pacing change:
#   PassBlur::PassBlur + 0xd4 (file/VA 0x421804)
#   bl property_get_int32 -> mov w0,#12
#
# The constructor then executes the unchanged x1,000,000 conversion and stores
# 12,000,000 ns at PassBlur + 0xf8. drawPassBlurIfNeed's sfScale==1 path uses
# threshold/2, giving an effective normal gate of about 6 ms.
PATCHES = (
    (0xD0, bytes.fromhex("00807600000000000080")),
    (0x148, bytes.fromhex("60c5")),
    (0x421804, bytes.fromhex("80018052")),
    (0x421890, bytes.fromhex("66b71814")),
    (0x54F19C, bytes.fromhex("11011414")),
    (0x5592F0, bytes.fromhex("c2d81314")),
    (0x55A040, bytes.fromhex("f0d4")),
    (0x55A0D8, bytes.fromhex("4ed51314")),
    (0x55A278, bytes.fromhex("e9d41314")),
    (0xA4F180, bytes.fromhex(
        "fd7bbea9fd030091f30b00f9f30300aa001840b900040051601a00b9e0000035608200912b010094"
        "e00313aaf30b40f9fd7bc2a823010014f30b40f9fd7bc2a8c0035fd6200b00b4fd7bbca9fd030091"
        "f35301a9f55b02a9161440f9f71b00f9560000b4d60a40f900dc40f9400900b4170040f9df0200f1"
        "e41a40fac0080054000480d20e010094f50300aa400800b4140300b094021491e00314aa0a01009480"
        "2240f9600300b5932640f9130500b4600640f91f0016eb610400546082009101010094614200912000"
        "80d22000e0f800040091b30201a9802240f9a05e00a9601a40b9952200f900040011601a00b9608200"
        "91f5000094f71b40f9e00314aaf55b42a9f35341a9fd7bc4a8ef000014010440f93f0017eb21010054"
        "e00314aaea000094f71b40f9e00315aaf35341a9f55b42a9fd7bc4a8e2000014000040f9d9ffff1773"
        "0240f9d9ffff17000c80d2db000094f30300aa20feffb416fc00a9200080d2010080d2600e00f96082"
        "0091d700009480000034e00313aad1000094e7ffff17802640f9600200f9932600f9cbffff17f71b40"
        "f9f35341a9f55b42a9fd7bc4a8c0035fd6c0035fd6fd7bbda9fd030091f35301a9f30300aaf51300"
        "f9800000b413dc40f9530000b4730240f9140300b094021491e00314aaba000094952a40f9d50000b4"
        "a00a40f99f2a00f984ffff97e00315aab2000094e00314aa150c44f8150100b4010040f9220440f95f"
        "0013eb81020054a10240f9010000f9952a00f9e00314aaa8000094350200b4a00a40f90170009121fc"
        "df88e1010035a10e40f90040009100fcdfc83f0000ebe0179f1af51340f9f35341a9fd7bc3a8c0035f"
        "d6350040f9e00301aae6ffff1720008052f8ffff1700008052f6ffff17fd7bbaa9fd030091f35301a914"
        "0300b094021491f55b02a9f50300aaf76303a9962a40f9ef2300fd0f40201eb60000b4d30a40f97806"
        "40f91f0300eb80010054e041201ee00315aa83000094ef2340fd20008052805a00b9f35341a9f55b42"
        "a9f76343a9fd7bc6a8c0035fd677820091e12b00f9e00317aae20f0b29720000946072009100fcdf88"
        "e12b40f960020035c40e40f97342009160fedfc8e20f4b299f0000eba1010054e041201ee00318aa69"
        "00009420008052ef2340fd805a00b9e00317aaf35341a9f55b42a9f76343a9fd7bc6a85d000014e003"
        "15aa5f00009400008052f5ffff17000300b0005845b9c0035fd6fd7bbea9fd030091f35301a9130300"
        "b073021491e00313aa4e000094742a40f97f2a00f9740000b4800a40f918ffff97e00313aa48000094"
        "b40000b4e00314aaf35341a9fd7bc2a841000014f35341a9fd7bc2a8c0035fd6fd7bbda9fd030091f5"
        "5b02a9150300b0b5021491f35301a9f40315aaf60300aae00315aa35000094938e44f8130200b48002"
        "40f9010440f93f0016eb21020054608200912d000094210080526072009101fc9f8860820091290000"
        "94600240f9800200f9e00313aaf3feff97f35341a9e00315aaf55b42a9fd7bc3a820000014130040f9"
        "f40300aae9ffff170000000000000000880201f9e07bbfa9e00314aaf6feff97e07bc1a8ebfeeb17e0"
        "0313aa4cffff97400000353329ec17682240b93a27ec17b6ffff97f603002ab12aec17b6ffff974000"
        "8052162bec17fd7bbca9e007bfa9c7ffff97e007c1a89748e71731c2ff173cc2ff17e3c2ff17e6c2ff"
        "1729c6ff172cc6ff170bd7ff1706d7ff17"
    )),
)

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def main() -> int:
    if len(sys.argv) != 3:
        print(f"usage: {sys.argv[0]} STOCK_LIB OUTPUT_LIB", file=sys.stderr)
        return 2

    stock_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])
    data = bytearray(stock_path.read_bytes())

    actual = sha256(data)
    if actual != STOCK_SHA256:
        raise SystemExit(f"stock SHA256 mismatch: {actual}")
    if len(data) != EXPECTED_SIZE:
        raise SystemExit(f"stock size mismatch: {len(data)}")

    for offset, payload in PATCHES:
        end = offset + len(payload)
        if end > len(data):
            raise SystemExit(f"patch outside file at 0x{offset:x}")
        data[offset:end] = payload

    final = bytes(data)
    actual = sha256(final)
    if actual != PATCHED_SHA256:
        raise SystemExit(f"patched SHA256 mismatch: {actual}")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(final)
    print(f"stock={STOCK_SHA256}")
    print(f"previous={PREVIOUS_SHA256}")
    print(f"patched={PATCHED_SHA256}")
    print("pacing_patch=0x421804: mov w0,#12")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
