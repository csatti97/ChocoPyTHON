        # conslist: constructs a list on heap from stack
        addi sp, sp, -8                  # Saved FP and saved RA (unused at top level).
        sw ra, 4(sp)                     # return address
        sw fp, 0(sp)                     # control link
        addi fp, sp, 8                   # New fp is at old SP.

                                         # box a placeholder list
        lw a1, 0(fp)                     # load first element: length
        la a0, $.list$prototype          # load prototype address to a0
        beqz a1, conslist_done           # if length equals zero, end directly
        addi a1, a1, @listHeaderWords    # compute required space (in bytes)
        jal alloc2                       

        lw t0, 0(fp)                     # $t0 := length of list
        sw t0, @.__len__(a0)             # dump list length to the heap
        slli t1, t0, 2                   # multiplied by 4 (each element occupied 4 bytes)
        addi t2, a0, @.__elts__          # $t2 := last element pos. in heap
        add t2, t2, t1                   
        addi t2, t2, -4                  # $t2 := first element pos. in heap
        add t1, t1, fp                   # $t1 := first element pos. in stack
conslist_1:
        lw t3, 0(t1)                     # load element to t3 from stack
        sw t3, 0(t2)                     # save element to list in heap
        addi t1, t1, -4                  # move $t1 to next element on stack
        addi t2, t2, -4                  # move $t2 to next element in heap
        addi t0, t0, -1                  # $t0 is list length. minus length of list until zero
        bnez t0, conslist_1              # $t0 is cnter of list length
conslist_done:
        lw ra, -4(fp)                    # epilogue
        lw fp, -8(fp)
        addi sp, sp, 8
        jr ra
