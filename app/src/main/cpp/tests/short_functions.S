.text
.p2align 2
.global tiny_getter
.type tiny_getter, %function
tiny_getter:
    mov w0, #169
    ret
.size tiny_getter, .-tiny_getter

// Match LunarCabbage: an eight-byte getter, a four-byte method, then SuperSkill.
.global tiny_attribute
.type tiny_attribute, %function
tiny_attribute:
    ret
.size tiny_attribute, .-tiny_attribute

.global tiny_super
.type tiny_super, %function
tiny_super:
    mov w0, #77
    ret
.size tiny_super, .-tiny_super

.global tiny_tail
.type tiny_tail, %function
tiny_tail:
    b tiny_super
.size tiny_tail, .-tiny_tail
.section .note.GNU-stack,"",%progbits
