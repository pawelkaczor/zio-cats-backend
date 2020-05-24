package zio.cats.backend.services

import zio.cats.backend.data.Error.{DatabaseError, UserNotFound}
import zio.cats.backend.data.{PostUser, User, UserId}
import zio.cats.backend.persistence.UserPersistence
import zio.cats.backend.services.reqres.reqres.ReqResClient
import zio.{Has, RIO, Task, UIO, ULayer, ZLayer}

package object user {

  type UserService = Has[UserService.Service]

  object UserService {
    trait Service {
      def createUser(postUser: PostUser): RIO[UserPersistence with ReqResClient, Unit]
      def getUser(userId: UserId): RIO[UserPersistence, User]
      def deleteUser(userId: UserId): RIO[UserPersistence, Unit]
    }

    val live: ULayer[UserService] =
      ZLayer.succeed(
        new UserService.Service {
          override def createUser(postUser: PostUser): RIO[UserPersistence with ReqResClient, Unit] =
            for {
              user <- ReqResClient.fetchUser(postUser.user_id)
              _    <- UserPersistence.create(user).mapError(err => DatabaseError(err.getMessage))
            } yield ()

          override def getUser(userId: UserId): RIO[UserPersistence, User] =
            UserPersistence
              .retrieve(userId)
              .mapError(err => DatabaseError(err.getMessage))
              .flatMap(maybeUser => Task.require(UserNotFound(userId))(Task.succeed(maybeUser)))

          override def deleteUser(userId: UserId): RIO[UserPersistence, Unit] =
            UserPersistence
              .delete(userId)
              .mapError(err => DatabaseError(err.getMessage))
              .flatMap {
                case true  => UIO.unit
                case false => Task.fail(UserNotFound(userId))
              }
        }
      )

    def createUser(postUser: PostUser): RIO[UserService with ReqResClient with UserPersistence, Unit] =
      RIO.accessM(_.get.createUser(postUser))

    def getUser(userId: UserId): RIO[UserService with UserPersistence, User] =
      RIO.accessM(_.get.getUser(userId))

    def deleteUser(userId: UserId): RIO[UserService with UserPersistence, Unit] =
      RIO.accessM(_.get.deleteUser(userId))
  }
}
