import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.{JsValue, Json}

import scalaj.http._

class RESTSpec extends FreeSpec with Matchers with ScalaFutures {
  """
    |AS a maintenance engineer
    |I WANT to add a location and set the bikes that it has
    |SO THAT bikes can be hired
  """.stripMargin - {
    "PUT /station/<id>" - {
      "Create/update a station with a set of bike IDs" in {
        val resp = PUT("/station/12345") {"""{
                                               "name": "West Road",
                                               "location": {
                                                 "lat": 3.20,
                                                 "long": 40.24
                                               },
                                               "availableBikes": [
                                                 "001","002","003","004"
                                               ]
                                             }"""}
        resp should (have('code(200)) or have ('code (201)))
        resp.headers should contain ("Location" -> "/station/12345")

        val url = resp.headers("Location")
        GET(url).body should be(Json.parse("""{
                                     "name": "West Road",
                                     "location": {
                                       "lat": 3.20,
                                       "long": 40.24
                                     },
                                     "availableBikes": [
                                       "001","002","003","004"
                                     ]
                                   }"""))
      }
    }
  }


  "GET /station/near/<lat>/<long>" - {
    "List nearest locations with current count of bikes per station - need a way to bootstrap this" in {}
  }
  "POST /station/<station_id>/bike" - {
    "Requests hire of a bike and returns a bike ID" in {}
  }
  "POST /station/<station_id>/bike/<bike_id>" - {
    "Notifies hire completed" in {}
  }
  "GET /station/depleted" - {
    "List locations with low number of bikes, together with near locations that have many bikes (ie. candidates for balancing)" in {}
  }

  def GET(url: String): HttpResponse[JsValue] =
    Http("http://localhost:9000" + url)
      .execute(is => Json.parse(HttpConstants.readString(is)))

  def PUT(url: String)(json: String): HttpResponse[String] =
    Http("http://localhost:9000" + url)
      .postData(json)
      .header("Content-Type", "application/json")
      .method("PUT")
      .asString
}
