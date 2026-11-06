package com.tim.dsmali.model

data class SmaliField(
    var accessFlags: MutableList<String> = mutableListOf(),
    var name: String = "",
    var type: String = "",
    val annotations: MutableList<SmaliAnnotation> = mutableListOf(),
    var initialValue: String? = null
)
