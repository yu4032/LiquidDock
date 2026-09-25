#!/usr/bin/env python3
import hashlib
import sys
from pathlib import Path

STOCK_SHA256 = "407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32"
PREVIOUS_SHA256 = "ef523f9d57ebe5c3af2ec39747b07ab6d70bd69165461e09ddc0fc8f13f5d563"
PACING12_SHA256 = "7cb2123d0b5d5cfd9ae63699c9624ebe306392b88f642ec609fdb5fc2247e30f"
PATCHED_SHA256 = "0ce7ceea23efd5a9f7bdd5f7858e3e8d21bb77c5d9261088a18726eeccf5bc48"
EXPECTED_SIZE = 11577024

# Reproduces the user-validated per-PassBlur early freshness patch, but removes the
# predecessor's late pre-queue cancellation and keeps stock pacing.
#
# The structural fix is at releaseCurRes:
#   0x54e5f4: future<bool>::get()/move -> ready-only helper at 0xa4f660
#
# Helper semantics:
#   - acquire-load shared-state ready flag at +0x70;
#   - if not ready, return false immediately without locking/waiting;
#   - if ready, tail-call the original __assoc_state<bool>::move().
#
# This prevents SurfaceFlinger composition from synchronously joining PassBlur work.
# The worker-entry stale gate remains, so queued obsolete jobs are still cheap to discard.
UPGRADE_PATCHES = (
    # Restore stock behavior removed by the predecessor/Pacing12 experiments.
    (0x421804, bytes.fromhex("67811894")),  # bl property_get_int32
    (0x55A040, bytes.fromhex("90ac1394")),  # bl PassBlur::queuePassBlurBuffer
    (0x55A0D8, bytes.fromhex("36008052")),  # mov w22,#1
)

PATCHES = (
    # ELF RX-segment extension from the validated predecessor module.
    (0xD0, bytes.fromhex("00807600000000000080")),
    (0x148, bytes.fromhex("60c5")),

    # Per-PassBlur registration / worker-entry freshness / lifecycle cleanup.
    (0x421890, bytes.fromhex("66b71814")),
    (0x54F19C, bytes.fromhex("11011414")),
    (0x5592F0, bytes.fromhex("c2d81314")),
    (0x55A278, bytes.fromhex("e9d41314")),
    (0xA4F180, bytes.fromhex(
        "fd7bbea9fd030091f30b00f9f30300aa001840b900040051601a00b9e0000035608200912b010094e00313aaf30b40f9fd7bc2a823010014f30b40f9fd7bc2a8c0035fd6200b00b4fd7bbca9fd030091f35301a9f55b02a9161440f9f71b00f9560000b4d60a40f900dc40f9400900b4170040f9df0200f1e41a40fac0080054000480d20e010094f50300aa400800b4140300b094021491e00314aa0a010094802240f9600300b5932640f9130500b4600640f91f0016eb61040054608200910101009461420091200080d22000e0f800040091b30201a9802240f9a05e00a9601a40b9952200f900040011601a00b960820091f5000094f71b40f9e00314aaf55b42a9f35341a9fd7bc4a8ef000014010440f93f0017eb21010054e00314aaea000094f71b40f9e00315aaf35341a9f55b42a9fd7bc4a8e2000014000040f9d9ffff17730240f9d9ffff17000c80d2db000094f30300aa20feffb416fc00a9200080d2010080d2600e00f960820091d700009480000034e00313aad1000094e7ffff17802640f9600200f9932600f9cbffff17f71b40f9f35341a9f55b42a9fd7bc4a8c0035fd6c0035fd6fd7bbda9fd030091f35301a9f30300aaf51300f9800000b413dc40f9530000b4730240f9140300b094021491e00314aaba000094952a40f9d50000b4a00a40f99f2a00f984ffff97e00315aab2000094e00314aa150c44f8150100b4010040f9220440f95f0013eb81020054a10240f9010000f9952a00f9e00314aaa8000094350200b4a00a40f90170009121fcdf88e1010035a10e40f90040009100fcdfc83f0000ebe0179f1af51340f9f35341a9fd7bc3a8c0035fd6350040f9e00301aae6ffff1720008052f8ffff1700008052f6ffff17fd7bbaa9fd030091f35301a9140300b094021491f55b02a9f50300aaf76303a9962a40f9ef2300fd0f40201eb60000b4d30a40f9780640f91f0300eb80010054e041201ee00315aa83000094ef2340fd20008052805a00b9f35341a9f55b42a9f76343a9fd7bc6a8c0035fd677820091e12b00f9e00317aae20f0b29720000946072009100fcdf88e12b40f960020035c40e40f97342009160fedfc8e20f4b299f0000eba1010054e041201ee00318aa6900009420008052ef2340fd805a00b9e00317aaf35341a9f55b42a9f76343a9fd7bc6a85d000014e00315aa5f00009400008052f5ffff17000300b0005845b9c0035fd6fd7bbea9fd030091f35301a9130300b073021491e00313aa4e000094742a40f97f2a00f9740000b4800a40f918ffff97e00313aa48000094b40000b4e00314aaf35341a9fd7bc2a841000014f35341a9fd7bc2a8c0035fd6fd7bbda9fd030091f55b02a9150300b0b5021491f35301a9f40315aaf60300aae00315aa35000094938e44f8130200b4800240f9010440f93f0016eb21020054608200912d000094210080526072009101fc9f886082009129000094600240f9800200f9e00313aaf3feff97f35341a9e00315aaf55b42a9fd7bc3a820000014130040f9f40300aae9ffff170000000000000000880201f9e07bbfa9e00314aaf6feff97e07bc1a8ebfeeb17e00313aa4cffff97400000353329ec17682240b93a27ec17b6ffff97f603002ab12aec17b6ffff9740008052162bec17fd7bbca9e007bfa9c7ffff97e007c1a89748e71731c2ff173cc2ff17e3c2ff17e6c2ff1729c6ff172cc6ff170bd7ff1706d7ff17"
    )),

    # releaseCurRes(): replace blocking future<bool>::move() with ready-only helper.
    (0xA4F660, bytes.fromhex(
        "08c00191"  # add x8, x0, #0x70
        "08fddf88"  # ldar w8, [x8]
        "48000036"  # tbz w8, #0, not_ready
        "75fde817"  # b 0x48ec40 (__assoc_state<bool>::move)
        "00008052"  # mov w0, #0
        "c0035fd6"  # ret
    )),
    (0x54E5F4, bytes.fromhex("1b041494")),
)

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def emit_patch_blobs(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    emitted = {}
    for offset, payload in (*PATCHES, *UPGRADE_PATCHES):
        previous = emitted.get(offset)
        if previous is not None and previous != payload:
            raise RuntimeError(f"conflicting patch blob at 0x{offset:x}")
        emitted[offset] = payload
    for offset, payload in emitted.items():
        (output_dir / f"{offset:08x}.bin").write_bytes(payload)


def main() -> int:
    if len(sys.argv) == 3 and sys.argv[1] == "--emit-patches":
        emit_patch_blobs(Path(sys.argv[2]))
        for offset, payload in (*PATCHES, *UPGRADE_PATCHES):
            print(f"patch=0x{offset:x} size={len(payload)}")
        return 0

    if len(sys.argv) != 3:
        print(
            f"usage: {sys.argv[0]} STOCK_LIB OUTPUT_LIB\n"
            f"       {sys.argv[0]} --emit-patches OUTPUT_DIR",
            file=sys.stderr,
        )
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
    print(f"pacing12={PACING12_SHA256}")
    print(f"patched={PATCHED_SHA256}")
    print("release_patch=0x54e5f4: ready-only future helper")
    print("pacing=stock property/default")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
