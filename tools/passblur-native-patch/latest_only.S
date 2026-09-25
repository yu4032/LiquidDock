.text
.p2align 2

.equ STATE_LOCK,           0x00
.equ STATE_STALE_TPIDR,    0x08
.equ STATE_STALE_PASSBLUR, 0x10
.equ STATE_TABLE,          0x40
.equ SLOT_SIZE,            0x20
.equ SLOT_COUNT,           128
.equ SLOT_KEY,             0x00
.equ SLOT_SUBMITTED,       0x08
.equ SLOT_CONSUMED,        0x10
.equ SLOT_MIN_VALID,       0x18

.global ld_submit_hook
.type ld_submit_hook,%function
ld_submit_hook:
    stp x29, x30, [sp, #-0x60]!
    sub sp, sp, #0x40
    stp x0, x1, [sp, #0x00]
    stp x2, x3, [sp, #0x10]
    stp x4, x5, [sp, #0x20]
    stp x6, x7, [sp, #0x30]
    cbz x4, 1f
    ldr x0, [x4, #0x10]
    cbz x0, 1f
    bl ld_state_submit
1:
    ldp x0, x1, [sp, #0x00]
    ldp x2, x3, [sp, #0x10]
    ldp x4, x5, [sp, #0x20]
    ldp x6, x7, [sp, #0x30]
    add sp, sp, #0x40
    b ORIG_SUBMIT_CONT
.size ld_submit_hook, .-ld_submit_hook

.global ld_closure_hook
.type ld_closure_hook,%function
ld_closure_hook:
    sub sp, sp, #0x20
    stp x30, x0, [sp, #0x00]
    ldr w9, [x0, #0x20]
    str w9, [sp, #0x10]
    str wzr, [sp, #0x14]

    bl ld_state_worker_enter
    cbz w0, 2f

    mov w10, #1
    str w10, [sp, #0x14]
    ldr x9, [sp, #0x08]
    str wzr, [x9, #0x20]

    adrp x10, STATE_BASE
    add x10, x10, :lo12:STATE_BASE
    mrs x11, TPIDR_EL0
    str x11, [x10, #STATE_STALE_TPIDR]
    str x1,  [x10, #STATE_STALE_PASSBLUR]

2:
    ldr x0, [sp, #0x08]
    stp d15, d14, [sp, #-0xa0]!
    adr x30, ld_closure_after
    b ORIG_CLOSURE_CONT

ld_closure_after:
    ldr w9, [sp, #0x14]
    cbz w9, 3f

    ldr x10, [sp, #0x08]
    ldr w11, [sp, #0x10]
    str w11, [x10, #0x20]

    adrp x10, STATE_BASE
    add x10, x10, :lo12:STATE_BASE
    stp xzr, xzr, [x10, #STATE_STALE_TPIDR]
3:
    ldp x30, x10, [sp, #0x00]
    add sp, sp, #0x20
    ret
.size ld_closure_hook, .-ld_closure_hook

.global ld_dequeue_hook
.type ld_dequeue_hook,%function
ld_dequeue_hook:
    adrp x9, STATE_BASE
    add x9, x9, :lo12:STATE_BASE
    ldr x10, [x9, #STATE_STALE_TPIDR]
    cbz x10, 4f
    mrs x11, TPIDR_EL0
    cmp x10, x11
    b.ne 4f
    ldr x10, [x9, #STATE_STALE_PASSBLUR]
    cmp x10, x0
    b.ne 4f
    ret
4:
    sub sp, sp, #0xa0
    b ORIG_DEQUEUE_CONT
.size ld_dequeue_hook, .-ld_dequeue_hook

.global ld_settexture_hook
.type ld_settexture_hook,%function
ld_settexture_hook:
    sub sp, sp, #0x60
    sub sp, sp, #0x20
    stp x0, x1, [sp, #0x00]
    str x30, [sp, #0x10]
    bl ld_state_invalidate
    ldp x0, x1, [sp, #0x00]
    ldr x30, [sp, #0x10]
    add sp, sp, #0x20
    b ORIG_SETTEX_CONT
.size ld_settexture_hook, .-ld_settexture_hook

.type ld_lock,%function
ld_lock:
    adrp x9, STATE_BASE
    add x9, x9, :lo12:STATE_BASE
5:
    ldaxr w10, [x9]
    cbnz w10, 6f
    mov w10, #1
    stxr w11, w10, [x9]
    cbz w11, 7f
6:
    yield
    b 5b
7:
    ret
.size ld_lock, .-ld_lock

.type ld_unlock,%function
ld_unlock:
    stlr wzr, [x9]
    ret
.size ld_unlock, .-ld_unlock

.type ld_state_submit,%function
ld_state_submit:
    cbz x0, 19f
    stp x19, x30, [sp, #-0x10]!
    mov x19, x0
    bl ld_lock
    add x10, x9, #STATE_TABLE
    mov w11, #SLOT_COUNT
    mov x12, xzr
8:
    ldr x13, [x10, #SLOT_KEY]
    cmp x13, x19
    b.eq 10f
    cbnz x13, 9f
    cbnz x12, 9f
    mov x12, x10
9:
    add x10, x10, #SLOT_SIZE
    subs w11, w11, #1
    b.ne 8b
    cbz x12, 18f
    mov x10, x12
    str x19, [x10, #SLOT_KEY]
    mov x13, #1
    str x13, [x10, #SLOT_SUBMITTED]
    str xzr, [x10, #SLOT_CONSUMED]
    str x13, [x10, #SLOT_MIN_VALID]
    b 18f
10:
    ldr x13, [x10, #SLOT_SUBMITTED]
    add x13, x13, #1
    str x13, [x10, #SLOT_SUBMITTED]
    ldr x14, [x10, #SLOT_MIN_VALID]
    cbnz x14, 18f
    mov x14, #1
    str x14, [x10, #SLOT_MIN_VALID]
18:
    bl ld_unlock
    ldp x19, x30, [sp], #0x10
19:
    ret
.size ld_state_submit, .-ld_state_submit

.type ld_state_worker_enter,%function
ld_state_worker_enter:
    stp x19, x30, [sp, #-0x10]!
    mov x19, x0
    mov x1, xzr
    mov w0, wzr
    ldr x8, [x19, #0x28]
    cbz x8, 29f
    ldr x19, [x8, #0x10]
    mov x1, x19
    cbz x19, 29f
    bl ld_lock
    add x10, x9, #STATE_TABLE
    mov w11, #SLOT_COUNT
20:
    ldr x12, [x10, #SLOT_KEY]
    cmp x12, x19
    b.eq 21f
    add x10, x10, #SLOT_SIZE
    subs w11, w11, #1
    b.ne 20b
    mov w0, wzr
    b 28f
21:
    ldr x12, [x10, #SLOT_SUBMITTED]
    ldr x13, [x10, #SLOT_CONSUMED]
    cmp x13, x12
    b.hs 27f
    add x13, x13, #1
    str x13, [x10, #SLOT_CONSUMED]
    ldr x14, [x10, #SLOT_MIN_VALID]
    cmp x13, x14
    b.lo 26f
    cmp x13, x12
    b.ne 26f
    mov w0, wzr
    b 28f
26:
    mov w0, #1
    b 28f
27:
    mov w0, wzr
28:
    bl ld_unlock
29:
    mov x1, x19
    ldp x19, x30, [sp], #0x10
    ret
.size ld_state_worker_enter, .-ld_state_worker_enter

.type ld_state_invalidate,%function
ld_state_invalidate:
    cbz x0, 39f
    stp x19, x30, [sp, #-0x10]!
    mov x19, x0
    bl ld_lock
    add x10, x9, #STATE_TABLE
    mov w11, #SLOT_COUNT
30:
    ldr x12, [x10, #SLOT_KEY]
    cmp x12, x19
    b.eq 31f
    add x10, x10, #SLOT_SIZE
    subs w11, w11, #1
    b.ne 30b
    b 38f
31:
    ldr x12, [x10, #SLOT_SUBMITTED]
    add x12, x12, #1
    str x12, [x10, #SLOT_MIN_VALID]
38:
    bl ld_unlock
    ldp x19, x30, [sp], #0x10
39:
    ret
.size ld_state_invalidate, .-ld_state_invalidate
