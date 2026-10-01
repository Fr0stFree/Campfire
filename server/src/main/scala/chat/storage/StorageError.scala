package chat.storage

enum StorageError {
  case UserSessionNotFound(username: String)
  case UserSessionAlreadyExists(username: String)
}
