import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.{JsObject, JsValue, Json}

import scalaj.http._

class RESTSpec extends FreeSpec with Matchers with ScalaFutures {
  """
    |AS a maintenance engineer
    |I WANT to add a location and set the bikes that it has
    |SO THAT bikes can be hired
  """.stripMargin - {
    "PUT /station/<id>" - {
      "Create a station with a set of bike IDs" in {
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

        GET(resp.headers("Location")).body should be(Json.parse("""{
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
      "Update an existing station with new details" in {
        GET("/station/12345").body should be(Json.parse("""{
                                     "name": "West Road",
                                     "location": {
                                       "lat": 3.20,
                                       "long": 40.24
                                     },
                                     "availableBikes": [
                                       "001","002","003","004"
                                     ]
                                   }"""))
        val resp = PUT("/station/12345") {"""{
                                               "name": "South Road",
                                               "location": {
                                                 "lat": 3.23,
                                                 "long": 40.21
                                               },
                                               "availableBikes": [
                                                 "001","003","005"
                                               ]
                                             }"""}
        resp should (have('code(200)) or have ('code (201)))
        GET("/station/12345").body should be(Json.parse("""{
                                                           "name": "South Road",
                                                           "location": {
                                                             "lat": 3.23,
                                                             "long": 40.21
                                                           },
                                                           "availableBikes": [
                                                             "001","003","005"
                                                           ]
                                                         }"""))
      }
      "Remove given bike IDs from other stations" in {
        GET("/station/12345").body should be(Json.parse("""{
                                                           "name": "South Road",
                                                           "location": {
                                                             "lat": 3.23,
                                                             "long": 40.21
                                                           },
                                                           "availableBikes": [
                                                             "001","003","005"
                                                           ]
                                                         }"""))
        PUT("/station/67890") {"""{
                                   "name": "East Road",
                                   "location": {
                                     "lat": 3.03,
                                     "long": 40.01
                                   },
                                   "availableBikes": [
                                     "005"
                                   ]
                                 }"""} should (have('code(200)) or have ('code (201)))
        (GET("/station/12345").body \ "availableBikes").get should be (Json.parse("""["001","003"]"""))
        (GET("/station/67890").body \ "availableBikes").get should be (Json.parse("""["005"]"""))
      }
    }
  }
  """
    |AS a customer
    |I WANT to hire a bike near to me, and return it to a different location
    |SO THAT I can get somewhere
  """.stripMargin - {
    "GET /station/near/<lat>/<long>" - {
      "List locations within 0.01 deg of current location, with count of bikes per station" in {
        GET("/station/12345").body should be(Json.parse("""{
                                                           "name": "South Road",
                                                           "location": {
                                                             "lat": 3.23,
                                                             "long": 40.21
                                                           },
                                                           "availableBikes": [
                                                             "001","003"
                                                           ]
                                                         }"""))
        GET("/station/67890").body should be(Json.parse("""{
                                                           "name": "East Road",
                                                           "location": {
                                                             "lat": 3.03,
                                                             "long": 40.01
                                                           },
                                                           "availableBikes": [
                                                             "005"
                                                           ]
                                                         }"""))
        GET("/station/near/3.04/40.02").body should be (Json.parse(
          """
            {
              "items": [
                {
                  "name": "East Road",
                  "location": {
                    "lat": 3.03,
                    "long": 40.01
                  },
                  "availableBikeCount": 1
                }
              ]
            }
          """))
        PUT("/station/1000001") {"""{
                                   "name": "First Avenue",
                                   "location": {
                                     "lat": 3.05,
                                     "long": 40.00
                                   },
                                   "availableBikes": [
                                     "006", "007", "008", "009", "010"
                                   ]
                                 }"""}
        GET("/station/near/3.04/40.02").body should be (Json.parse(
          """
            {
              "items": [
                {
                  "name": "East Road",
                  "location": {
                    "lat": 3.03,
                    "long": 40.01
                  },
                  "availableBikeCount": 1
                },
                {
                  "name": "First Avenue",
                  "location": {
                    "lat": 3.05,
                    "long": 40.00
                  },
                  "availableBikeCount": 5
                }
              ]
            }
          """))
      }
    }
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

  def GET(url: String): HttpResponse[JsObject] =
    Http("http://localhost:9000" + url)
      .execute(is => Json.parse(HttpConstants.readString(is)).as[JsObject])

  def PUT(url: String)(json: String): HttpResponse[String] =
    Http("http://localhost:9000" + url)
      .postData(json)
      .header("Content-Type", "application/json")
      .method("PUT")
      .asString
}
