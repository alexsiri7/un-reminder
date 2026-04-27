package net.interstellarai.unreminder.service.notification

// XOR-fold high 32 bits into low 32 bits to avoid silent truncation for large IDs
internal fun Long.toRequestCode(): Int = (this xor (this ushr 32)).toInt()
