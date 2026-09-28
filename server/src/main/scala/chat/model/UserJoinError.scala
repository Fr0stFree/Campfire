package chat.model

enum UserJoinError {
  case UsernameTaken(username: String)
  case InvalidUsername(username: String)
}
