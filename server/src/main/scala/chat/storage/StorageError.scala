package chat.storage

enum StorageError:
  case ObjectDoesNotExist(username: String)
  case ObjectAlreadyExists(username: String)
