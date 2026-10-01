package chat.service

enum UserJoinError {
  case UsernameTaken(username: String)
  case InvalidUsername(username: String)
}
