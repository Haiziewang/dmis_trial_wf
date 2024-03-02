writeResult = db.<collection>.<cmd>

printjson(writeResult)
if (writeResult.hasWriteError()) {
    throw 1
}