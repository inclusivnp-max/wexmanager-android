package client.wexmanager.feature.editor.lang

/**
 * Мнемоники инструкций Dalvik (smali) и JVM (листинги javap/ASM и csmali). В десктопе брались из
 * dexlib2 и ASM; здесь — из таблиц clazzpatcher (com.tim.dex.data.Opcode, com.tim.clazz.model.opcodes.Opcodes).
 */
internal object Opcodes {
    /** Все опкоды DEX 035–039 плюс odex-формы (старые дампы baksmali). */
    val dalvik: Set<String> by lazy {
        words(
            """
            nop move move/from16 move/16 move-wide move-wide/from16 move-wide/16 move-object
            move-object/from16 move-object/16 move-result move-result-wide move-result-object move-exception
            return-void return return-wide return-object const/4 const/16 const const/high16 const-wide/16
            const-wide/32 const-wide const-wide/high16 const-string const-string/jumbo const-class
            monitor-enter monitor-exit check-cast instance-of array-length new-instance new-array
            filled-new-array filled-new-array/range fill-array-data throw goto goto/16 goto/32 packed-switch
            sparse-switch cmpl-float cmpg-float cmpl-double cmpg-double cmp-long if-eq if-ne if-lt if-ge if-gt
            if-le if-eqz if-nez if-ltz if-gez if-gtz if-lez aget aget-wide aget-object aget-boolean aget-byte
            aget-char aget-short aput aput-wide aput-object aput-boolean aput-byte aput-char aput-short iget
            iget-wide iget-object iget-boolean iget-byte iget-char iget-short iput iput-wide iput-object
            iput-boolean iput-byte iput-char iput-short sget sget-wide sget-object sget-boolean sget-byte
            sget-char sget-short sput sput-wide sput-object sput-boolean sput-byte sput-char sput-short
            invoke-virtual invoke-super invoke-direct invoke-static invoke-interface invoke-virtual/range
            invoke-super/range invoke-direct/range invoke-static/range invoke-interface/range neg-int not-int
            neg-long not-long neg-float neg-double int-to-long int-to-float int-to-double long-to-int
            long-to-float long-to-double float-to-int float-to-long float-to-double double-to-int
            double-to-long double-to-float int-to-byte int-to-char int-to-short add-int sub-int mul-int
            div-int rem-int and-int or-int xor-int shl-int shr-int ushr-int add-long sub-long mul-long
            div-long rem-long and-long or-long xor-long shl-long shr-long ushr-long add-float sub-float
            mul-float div-float rem-float add-double sub-double mul-double div-double rem-double add-int/2addr
            sub-int/2addr mul-int/2addr div-int/2addr rem-int/2addr and-int/2addr or-int/2addr xor-int/2addr
            shl-int/2addr shr-int/2addr ushr-int/2addr add-long/2addr sub-long/2addr mul-long/2addr
            div-long/2addr rem-long/2addr and-long/2addr or-long/2addr xor-long/2addr shl-long/2addr
            shr-long/2addr ushr-long/2addr add-float/2addr sub-float/2addr mul-float/2addr div-float/2addr
            rem-float/2addr add-double/2addr sub-double/2addr mul-double/2addr div-double/2addr
            rem-double/2addr add-int/lit16 rsub-int mul-int/lit16 div-int/lit16 rem-int/lit16 and-int/lit16
            or-int/lit16 xor-int/lit16 add-int/lit8 rsub-int/lit8 mul-int/lit8 div-int/lit8 rem-int/lit8
            and-int/lit8 or-int/lit8 xor-int/lit8 shl-int/lit8 shr-int/lit8 ushr-int/lit8 invoke-polymorphic
            invoke-polymorphic/range invoke-custom invoke-custom/range const-method-handle const-method-type
            """,
        ) + words(
            """
            iget-volatile iput-volatile sget-volatile sput-volatile iget-object-volatile iget-wide-volatile
            iput-wide-volatile sget-wide-volatile sput-wide-volatile iput-object-volatile sget-object-volatile
            sput-object-volatile throw-verification-error execute-inline execute-inline/range
            invoke-direct-empty invoke-object-init/range return-void-barrier return-void-no-barrier iget-quick
            iget-wide-quick iget-object-quick iput-quick iput-wide-quick iput-object-quick iput-boolean-quick
            iput-byte-quick iput-char-quick iput-short-quick iget-boolean-quick iget-byte-quick
            iget-char-quick iget-short-quick invoke-virtual-quick invoke-virtual-quick/range
            invoke-super-quick invoke-super-quick/range packed-switch-payload sparse-switch-payload
            array-payload
            """,
        )
    }

    /** Опкоды JVM: ASM печатает их ПРОПИСНЫМИ, javap и csmali — строчными. */
    val jvm: Set<String> by lazy {
        val upper = words(
            """
            NOP ACONST_NULL ICONST_M1 ICONST_0 ICONST_1 ICONST_2 ICONST_3 ICONST_4 ICONST_5 LCONST_0 LCONST_1
            FCONST_0 FCONST_1 FCONST_2 DCONST_0 DCONST_1 BIPUSH SIPUSH LDC LDC_W LDC2_W ILOAD LLOAD FLOAD
            DLOAD ALOAD ILOAD_0 ILOAD_1 ILOAD_2 ILOAD_3 LLOAD_0 LLOAD_1 LLOAD_2 LLOAD_3 FLOAD_0 FLOAD_1
            FLOAD_2 FLOAD_3 DLOAD_0 DLOAD_1 DLOAD_2 DLOAD_3 ALOAD_0 ALOAD_1 ALOAD_2 ALOAD_3 IALOAD LALOAD
            FALOAD DALOAD AALOAD BALOAD CALOAD SALOAD ISTORE LSTORE FSTORE DSTORE ASTORE ISTORE_0 ISTORE_1
            ISTORE_2 ISTORE_3 LSTORE_0 LSTORE_1 LSTORE_2 LSTORE_3 FSTORE_0 FSTORE_1 FSTORE_2 FSTORE_3 DSTORE_0
            DSTORE_1 DSTORE_2 DSTORE_3 ASTORE_0 ASTORE_1 ASTORE_2 ASTORE_3 IASTORE LASTORE FASTORE DASTORE
            AASTORE BASTORE CASTORE SASTORE POP POP2 DUP DUP_X1 DUP_X2 DUP2 DUP2_X1 DUP2_X2 SWAP IADD LADD
            FADD DADD ISUB LSUB FSUB DSUB IMUL LMUL FMUL DMUL IDIV LDIV FDIV DDIV IREM LREM FREM DREM INEG
            LNEG FNEG DNEG ISHL LSHL ISHR LSHR IUSHR LUSHR IAND LAND IOR LOR IXOR LXOR IINC I2L I2F I2D L2I
            L2F L2D F2I F2L F2D D2I D2L D2F I2B I2C I2S LCMP FCMPL FCMPG DCMPL DCMPG IFEQ IFNE IFLT IFGE IFGT
            IFLE IF_ICMPEQ IF_ICMPNE IF_ICMPLT IF_ICMPGE IF_ICMPGT IF_ICMPLE IF_ACMPEQ IF_ACMPNE GOTO JSR RET
            TABLESWITCH LOOKUPSWITCH IRETURN LRETURN FRETURN DRETURN ARETURN RETURN GETSTATIC PUTSTATIC
            GETFIELD PUTFIELD INVOKEVIRTUAL INVOKESPECIAL INVOKESTATIC INVOKEINTERFACE INVOKEDYNAMIC NEW
            NEWARRAY ANEWARRAY ARRAYLENGTH ATHROW CHECKCAST INSTANCEOF MONITORENTER MONITOREXIT WIDE
            MULTIANEWARRAY IFNULL IFNONNULL GOTO_W JSR_W BREAKPOINT IMPDEP1 IMPDEP2
            """,
        )
        upper + upper.map { it.lowercase() }
    }
}
