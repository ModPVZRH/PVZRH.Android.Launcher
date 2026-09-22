// Preserve the native call frame while recording ARM64's indirect result (X8).
// Per-hook stubs pass the managed destination in X16. Stack arguments stay at
// their original offsets because this bridge restores SP before tail-calling.
.text
.p2align 2
.global ReturnBufferBridge
.type ReturnBufferBridge, %function
ReturnBufferBridge:
    sub sp, sp, #224
    stp x0, x1, [sp, #0]
    stp x2, x3, [sp, #16]
    stp x4, x5, [sp, #32]
    stp x6, x7, [sp, #48]
    stp x8, x16, [sp, #64]
    str x30, [sp, #80]
    stp q0, q1, [sp, #96]
    stp q2, q3, [sp, #128]
    stp q4, q5, [sp, #160]
    stp q6, q7, [sp, #192]
    mov x0, x8
    bl SetReturnBuffer
    ldp q0, q1, [sp, #96]
    ldp q2, q3, [sp, #128]
    ldp q4, q5, [sp, #160]
    ldp q6, q7, [sp, #192]
    ldp x0, x1, [sp, #0]
    ldp x2, x3, [sp, #16]
    ldp x4, x5, [sp, #32]
    ldp x6, x7, [sp, #48]
    ldp x8, x16, [sp, #64]
    ldr x30, [sp, #80]
    add sp, sp, #224
    br x16
.size ReturnBufferBridge, .-ReturnBufferBridge
.section .note.GNU-stack,"",%progbits
