import awscala._, dynamodbv2._

object DBManager {
  val tableName = "Polls"
  val ownerAttributeName = "Owner"
  implicit val dynamoDB: DynamoDB = DynamoDB.at(Region.Tokyo)
  val table: Table = dynamoDB.table(tableName).get

  def addPoll(pollMessageId: Int, ownerChatId: Long): Unit = {
    table.put(pollMessageId, ownerAttributeName -> ownerChatId)
  }

  def getPolls(ownerChatId: Long): Seq[Int] = {
    table.scan(Seq(ownerAttributeName -> cond.eq(ownerChatId)))
      .map(_.attributes.headOption)
      .collect { case Some(value) => value }
      .map(_.value.n)
      .collect { case Some(value) => value }
      .map(_.toInt)
  }

  def deletePoll(pollMessageId: Int): Unit = {
    table.delete(pollMessageId)
  }
}
