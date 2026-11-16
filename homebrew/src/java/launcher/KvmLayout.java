/* SPDX-License-Identifier: MIT */
/**
 * Audited KVM object/method layout shared by the supported bootstrap families.
 * All values are byte offsets unless their name says otherwise. These describe
 * live records, not Java class-file structures. No methods allocate objects.
 */
final class KvmLayout {
    static final int ARRAY_DATA = NativeProtocol.KVM_ARRAY_DATA_BYTES;
    static final int CLASS_BYTES = 40;
    static final int CLASS_METHOD_TABLE = 36;
    static final int METHOD_TABLE_HEADER = 4;
    static final int METHOD_BYTES = 32;
    static final int METHOD_MAX_COUNT = 512;
    static final int METHOD_CODE = 4;
    static final int METHOD_CODE_LENGTH = 16;
    static final int METHOD_FLAGS = 20;
    static final int METHOD_OWNER = 24;
    static final int METHOD_SIGNATURE = 28;
    static final int METHOD_ARGUMENT_SHIFT = 16;
    static final int FLAGS_STATIC = 0x008;
    static final int FLAGS_NATIVE_STATIC = 0x108;
    static final int FLAGS_ABSTRACT = 0x400;
    static final int FLAGS_PRIVATE_STATIC_NATIVE_VOID = 0xC10A;
    static final int CACHE_LINE_BYTES = 32;

    private KvmLayout() {}
}
