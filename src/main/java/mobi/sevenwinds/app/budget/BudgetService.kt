package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRequestRecord): BudgetRequestRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetRequestEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.authorId = body.authorId
            }
            return@transaction entity.fromRequest()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val expression: Op<Boolean> = if (param.author_name == null) {
                BudgetTable.year eq param.year
            } else {
                BudgetTable.year eq param.year and (AuthorTable.name.lowerCase() like "%" + param.author_name.toLowerCase() + "%")
            }

            val query = Join(
                BudgetTable, AuthorTable, JoinType.LEFT, onColumn = BudgetTable.authorId, otherColumn = AuthorTable.id
            ).slice(
                BudgetTable.id,
                BudgetTable.year,
                BudgetTable.month,
                BudgetTable.amount,
                BudgetTable.type,
                AuthorTable.name
            )
                .select { expression }
                .limit(param.limit, param.offset).orderBy(BudgetTable.month)
                .orderBy(BudgetTable.amount to SortOrder.DESC)

            val total = query.count()
            val data = BudgetResponseEntity.wrapRows(query).map {
                var an: String? = if (it.readValues.get(AuthorTable.name) == null)  null else it.authorName
                BudgetResponseRecord(
                    year = it.year, month = it.month, amount = it.amount, type = it.type, authorName = an
                )
            }

            val sumByType = data.groupBy { it.type.name }.mapValues { it.value.sumOf { v -> v.amount } }

            return@transaction BudgetYearStatsResponse(
                total = total, totalByType = sumByType, items = data
            )
        }
    }
}