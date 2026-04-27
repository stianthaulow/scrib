package dev.thaulow.scrib.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun File.writeAtomically(text: String) {
  val parent = parentFile ?: error("File has no parent: $path")
  parent.mkdirs()
  val tmp = File(parent, "$name.tmp")
  try {
    tmp.writeText(text, Charsets.UTF_8)
    RandomAccessFile(tmp, "rws").use { it.fd.sync() }
    Files.move(
      tmp.toPath(),
      toPath(),
      StandardCopyOption.REPLACE_EXISTING,
      StandardCopyOption.ATOMIC_MOVE,
    )
  } catch (e: Exception) {
    tmp.delete()
    throw e
  }
}
