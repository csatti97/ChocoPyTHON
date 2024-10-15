# makebool.s
addi sp, sp, -4
sw t0, 0(sp)
slli a0, a0, 4
la t0, @bool.False
add a0, a0, t0
lw t0, 0(sp)
addi sp, sp, 4
jr ra
