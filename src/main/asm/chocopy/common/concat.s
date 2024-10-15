        addi sp, sp, -32
        sw ra, 28(sp)
        sw fp, 24(sp)
        addi fp, sp, 32
	sw s1, -12(fp)
        sw s2, -16(fp)
        sw s3, -20(fp)
	sw s4, -24(fp)
        sw s5, -28(fp)
        lw t0, 4(fp)  # arr1
        lw t1, 0(fp)  # arr2
        beqz t0, concat_none
        beqz t1, concat_none
        lw t0, @.__len__(t0)
        lw t1, @.__len__(t1)
        add s5, t0, t1                    # $s5 = len(arr1) + len(arr2)
        addi a1, s5, @listHeaderWords     # allocate space for returned list
        la a0, $.list$prototype
        jal alloc2
        sw s5, @.__len__(a0)
	mv s5, a0                         # $s5 = arr_ret
        addi s3, s5, @.__elts__           # $s3 = position of elements in returned addr.
        lw s1, 4(fp)                      # $s1 = arr1
	lw s2, @.__len__(s1)              # $s2 = len(arr1)
        addi s1, s1, @.__elts__           # $s1 = arr1.elems
	lw s4, 12(fp)                     # $s4 = conversion
concat_1: # append arr1 to the list
        beqz s2, concat_2
        lw a0, 0(s1)
	jalr ra, s4, 0
        sw a0, 0(s3)
        addi s2, s2, -1
        addi s1, s1, 4
        addi s3, s3, 4
        j concat_1
concat_2:
        lw s1, 0(fp)                # $s1 = arr2
        lw s2, @.__len__(s1)        # $s2 = arr2.length
        addi s1, s1, @.__elts__     # $s1 = arr.elems
	lw s4, 8(fp)                # $s4 = conversion
concat_3:
        beqz s2, concat_4
        lw a0, 0(s1)
	jalr ra, s4, 0
        sw a0, 0(s3)
        addi s2, s2, -1
        addi s1, s1, 4
        addi s3, s3, 4
        j concat_3
concat_4: # epilogue
	mv a0, s5
        lw s1, -12(fp)
        lw s2, -16(fp)
        lw s3, -20(fp)
	lw s4, -24(fp)
        lw s5, -28(fp)
        lw ra, -4(fp)
        lw fp, -8(fp)
        addi sp, sp, 32
        jr ra
concat_none:
        j error.None
