.text
.p2align 2
.global live_x17
.type live_x17, %function
live_x17:
    mov w17, #169
    mov w0, w17
    ret
.size live_x17, .-live_x17

.global live_x16
.type live_x16, %function
live_x16:
    mov w16, #170
    mov w0, w16
    ret
.size live_x16, .-live_x16

// Model the hook already installed by libNebula on SetPlant/TakeDamage.
.hidden prior_hook
.global chain_adrp_x17
.type chain_adrp_x17, %function
chain_adrp_x17:
    adrp x17, prior_hook
    add x17, x17, :lo12:prior_hook
    br x17
.size chain_adrp_x17, .-chain_adrp_x17

.global chain_adrp_x16
.type chain_adrp_x16, %function
chain_adrp_x16:
    adrp x16, prior_hook
    add x16, x16, :lo12:prior_hook
    br x16
.size chain_adrp_x16, .-chain_adrp_x16

.global chain_ldr_x17
.type chain_ldr_x17, %function
chain_ldr_x17:
    ldr x17, .Ldestination17
    br x17
.size chain_ldr_x17, .-chain_ldr_x17

.global chain_ldr_x16
.type chain_ldr_x16, %function
chain_ldr_x16:
    ldr x16, .Ldestination16
    br x16
.size chain_ldr_x16, .-chain_ldr_x16

// PIE relocations belong in RELRO data, not in executable text.
.section .data.rel.ro,"aw",%progbits
.p2align 3
.Ldestination17:
    .xword prior_hook
.Ldestination16:
    .xword prior_hook
.section .note.GNU-stack,"",%progbits
