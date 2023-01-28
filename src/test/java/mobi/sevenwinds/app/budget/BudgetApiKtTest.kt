package mobi.sevenwinds.app.budget

import io.restassured.RestAssured
import mobi.sevenwinds.app.author.AuthorRecord
import mobi.sevenwinds.common.ServerTest
import mobi.sevenwinds.common.jsonBody
import mobi.sevenwinds.common.toResponse
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BudgetApiKtTest : ServerTest() {

    enum class AuthorName(val id: Int) {
        Иванов(1),
        Петров(2)
    }

    @BeforeEach
    internal fun setUp() {
        transaction { BudgetTable.deleteAll() }
    }

    @Test
    fun testBudgetPagination() {
        addAuthor(AuthorRecord(AuthorName.Иванов.name))
        addAuthor(AuthorRecord(AuthorName.Петров.name))

        addRecord(BudgetRequestRecord(2020, 5, 10, BudgetType.Приход, AuthorName.Иванов.id))
        addRecord(BudgetRequestRecord(2020, 5, 5, BudgetType.Приход, AuthorName.Петров.id))
        addRecord(BudgetRequestRecord(2020, 5, 20, BudgetType.Приход, AuthorName.Петров.id))
        addRecord(BudgetRequestRecord(2020, 5, 30, BudgetType.Приход, AuthorName.Иванов.id))
        addRecord(BudgetRequestRecord(2020, 5, 40, BudgetType.Приход, AuthorName.Иванов.id))
        addRecord(BudgetRequestRecord(2030, 1, 1, BudgetType.Расход, AuthorName.Петров.id))

        RestAssured.given()
            .queryParam("year", 2020)
            .queryParam("limit", 100)
            .queryParam("offset", 0)
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println("${response.total} / ${response.items} / ${response.totalByType}")

                Assert.assertEquals(5, response.total)
                Assert.assertEquals(5, response.items.size)
                Assert.assertEquals(105, response.totalByType[BudgetType.Приход.name])
            }
    }

    @Test
    fun testStatsSortOrder() {
        addAuthor(AuthorRecord(AuthorName.Иванов.name))
        addAuthor(AuthorRecord(AuthorName.Петров.name))

        addRecord(BudgetRequestRecord(2020, 5, 100, BudgetType.Приход, AuthorName.Иванов.id))
        addRecord(BudgetRequestRecord(2020, 1, 5, BudgetType.Приход, AuthorName.Петров.id))
        addRecord(BudgetRequestRecord(2020, 5, 50, BudgetType.Приход, AuthorName.Петров.id))
        addRecord(BudgetRequestRecord(2020, 1, 30, BudgetType.Приход, AuthorName.Иванов.id))
        addRecord(BudgetRequestRecord(2020, 5, 400, BudgetType.Приход, AuthorName.Иванов.id))

        // expected sort order - month ascending, amount descending

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                response.items
                println(response.items)

                Assert.assertEquals(30, response.items[0].amount)
                Assert.assertEquals(5, response.items[1].amount)
                Assert.assertEquals(400, response.items[2].amount)
                Assert.assertEquals(100, response.items[3].amount)
                Assert.assertEquals(50, response.items[4].amount)
            }
    }

    @Test
    fun testInvalidMonthValues() {
        RestAssured.given()
            .jsonBody(BudgetRequestRecord(2020, -5, 5, BudgetType.Приход, AuthorName.Иванов.id))
            .post("/budget/add")
            .then().statusCode(400)

        RestAssured.given()
            .jsonBody(BudgetRequestRecord(2020, 15, 5, BudgetType.Приход, AuthorName.Иванов.id))
            .post("/budget/add")
            .then().statusCode(400)
    }

    private fun addRecord(record: BudgetRequestRecord) {
        RestAssured.given()
            .jsonBody(record)
            .post("/budget/add")
            .toResponse<BudgetRequestRecord>().let { response ->
                Assert.assertEquals(record, response)
            }
    }

    private fun addAuthor(author: AuthorRecord) {
        RestAssured.given()
            .jsonBody(author)
            .post("/author/add")
            .toResponse<AuthorRecord>().let { response ->
                Assert.assertEquals(author, response)
            }
    }

    @Test
    fun testBudgetByAuthor() {
        addAuthor(AuthorRecord(AuthorName.Иванов.name))
        addAuthor(AuthorRecord(AuthorName.Петров.name))

        addRecord(BudgetRequestRecord(2020, 5, 10, BudgetType.Приход, AuthorName.Иванов.id))
        addRecord(BudgetRequestRecord(2020, 5, 5, BudgetType.Приход, AuthorName.Петров.id))
        addRecord(BudgetRequestRecord(2020, 5, 20, BudgetType.Приход, AuthorName.Петров.id))
        addRecord(BudgetRequestRecord(2020, 5, 30, BudgetType.Приход, AuthorName.Иванов.id))
        addRecord(BudgetRequestRecord(2020, 5, 40, BudgetType.Приход, AuthorName.Иванов.id))
        addRecord(BudgetRequestRecord(2030, 1, 1, BudgetType.Расход, AuthorName.Петров.id))

        RestAssured.given()
            .queryParam("year", 2020)
            .queryParam("limit", 100)
            .queryParam("offset", 0)
            .queryParam("author_name", AuthorName.Иванов.name)
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println("${response.total} / ${response.items} / ${response.totalByType}")

                Assert.assertEquals(3, response.total)
                Assert.assertEquals(3, response.items.size)
                Assert.assertEquals(80, response.totalByType[BudgetType.Приход.name])
            }
    }
}