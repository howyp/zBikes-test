
import java.io.InputStream
import com.github.tomakehurst.wiremock._
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalactic.Fail
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.util.{Failure, Success, Try}
import scalaj.http._

import spray.json._
import DefaultJsonProtocol._

class RESTSpec extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  "DELETE /station/all" - {
    "Remove all stations" in {
      DELETE("/station/all") should have ('code (200))
    }
  }

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
        resp.headers.get("Location").get should endWith ("/station/12345")

        GET(resp.headers("Location")).body should be("""{
                                     "name": "West Road",
                                     "location": {
                                       "lat": 3.20,
                                       "long": 40.24
                                     },
                                     "availableBikes": [
                                       "001","002","003","004"
                                     ]
                                   }""".parseJson)
      }
      "Update an existing station with new details" in {
        GET("/station/12345").body should be("""{
                                     "name": "West Road",
                                     "location": {
                                       "lat": 3.20,
                                       "long": 40.24
                                     },
                                     "availableBikes": [
                                       "001","002","003","004"
                                     ]
                                   }""".parseJson)
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
        GET("/station/12345").body should be("""{
                                                           "name": "South Road",
                                                           "location": {
                                                             "lat": 3.23,
                                                             "long": 40.21
                                                           },
                                                           "availableBikes": [
                                                             "001","003","005"
                                                           ]
                                                         }""".parseJson)
      }
      "Remove given bike IDs from other stations" in {
        GET("/station/12345").body should be("""{
                                                           "name": "South Road",
                                                           "location": {
                                                             "lat": 3.23,
                                                             "long": 40.21
                                                           },
                                                           "availableBikes": [
                                                             "001","003","005"
                                                           ]
                                                         }""".parseJson)
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
        GET("/station/12345").body.fields("availableBikes") should be ("""["001","003"]""".parseJson)
        GET("/station/67890").body.fields("availableBikes") should be( """["005"]""".parseJson)
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
        GET("/station/12345").body should be("""{
                                                           "name": "South Road",
                                                           "location": {
                                                             "lat": 3.23,
                                                             "long": 40.21
                                                           },
                                                           "availableBikes": [
                                                             "001","003"
                                                           ]
                                                         }""".parseJson)
        GET("/station/67890").body should be("""{
                                                           "name": "East Road",
                                                           "location": {
                                                             "lat": 3.03,
                                                             "long": 40.01
                                                           },
                                                           "availableBikes": [
                                                             "005"
                                                           ]
                                                         }""".parseJson)
        GET("/station/near/3.04/40.02").body should be (
          """
            {
              "items": [
                {
                  "name": "East Road",
                  "location": {
                    "lat": 3.03,
                    "long": 40.01
                  },
                  "availableBikeCount": 1,
                  "selfUrl": "/station/67890",
                  "hireUrl": "/station/67890/bike"
                }
              ]
            }
          """.parseJson)
        PUT("/station/1000001") {"""{
                                   "name": "First Avenue",
                                   "location": {
                                     "lat": 3.05,
                                     "long": 40.01
                                   },
                                   "availableBikes": [
                                     "006", "007", "008", "009", "010"
                                   ]
                                 }"""}
        GET("/station/near/3.04/40.02").body should be (
          """
            {
              "items": [
                {
                  "name": "East Road",
                  "location": {
                    "lat": 3.03,
                    "long": 40.01
                  },
                  "availableBikeCount": 1,
                  "selfUrl": "/station/67890",
                  "hireUrl": "/station/67890/bike"
                },
                {
                  "name": "First Avenue",
                  "location": {
                    "lat": 3.05,
                    "long": 40.01
                  },
                  "availableBikeCount": 5,
                  "selfUrl": "/station/1000001",
                  "hireUrl": "/station/1000001/bike"
                }
              ]
            }
          """.parseJson)
      }
      "Return an empty listing when there are no stations within 0.01 deg of current location" in {
        GET("/station/near/1.00/5.00").body should be (
          """
            {
              "items": []
            }
          """.parseJson)
      }
    }
    "POST /station/<station_id>/bike" - {
      "Requests hire of a bike and returns a bike ID" in {
        val (selfUrl, hireUrl) = tupleOfUrls(GET("/station/near/3.04/40.02").body.fields("items").convertTo[JsArray].elements(1))
        stubFor(
          get(urlEqualTo("/customer/badrida382"))
          .willReturn(
              aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(
                """
                  {
                    "username": "badrida382",
                    "firstname": "Joe",
                    "lastname": "Bloggs"
                  }
                """
            )
        ))
        val resp = POST(hireUrl) {
          """
            {
              "action": "hire",
              "username": "badrida382"
            }
          """
        }
        resp should have ('code (200))
        resp.body should be ("""{ "bikeId": "006" }""".parseJson)
        GET(selfUrl).body.fields("availableBikes") should be ("""["007","008","009","010"]""".parseJson)
      }
      "Returns bike IDs in order" in {
        GET("/station/12345").body.fields("availableBikes") should be ("""["001","003"]""".parseJson)
        POST("/station/12345/bike") {
          """
            {
              "action": "hire",
              "username": "badrida382"
            }
          """
        }.body should be( """{ "bikeId": "001" }""".parseJson)
        POST("/station/12345/bike") {
          """
            {
              "action": "hire",
              "username": "badrida382"
            }
          """
        }.body should be( """{ "bikeId": "003" }""".parseJson)
      }
      "Returns 404 if no bikes are available" in {
        GET("/station/12345").body.fields("availableBikes") should be ("""[]""".parseJson)
        POST("/station/12345/bike") {
          """
            {
              "action": "hire",
              "username": "badrida382"
            }
          """
        } should (have ('code (404)) or have ('code (204)))
      }
      "Returns 401 if GET /customer/<username> indicates that the customer is unauthorised" in {
        GET("/station/67890").body.fields("availableBikes") should be ("""["005"]""".parseJson)
        stubFor(
          get(urlEqualTo("/customer/i_am_not_authorised"))
            .willReturn(
              aResponse()
                .withStatus(401)
            )
        )
        POST("/station/67890/bike") {
          """
            {
              "action": "hire",
              "username": "i_am_not_authorised"
            }
          """
        } should have ('code (401))
      }
    }
    "POST /station/<station_id>/bike/<bike_id>" - {
      "Notifies hire completed for a bike that is currently hired out" in {
        POST("/station/12345/bike/006") {
          """
            {
              "action": "return",
              "username": "badrida382"
            }
          """
        } should have ('code (200))
        GET("/station/12345").body.fields("availableBikes") should be ("""["006"]""".parseJson)
      }
      "Returns 404 if the bike ID is unknown" in {
        POST("/station/12345/bike/999") {
          """
            {
              "action": "return",
              "username": "badrida382"
            }
          """
        } should have ('code (404))
      }
      "Returns 409 if the bike ID is not currently hired" in {
        POST("/station/12345/bike/008") {
          """
            {
              "action": "return",
              "username": "badrida382"
            }
          """
        } should have ('code (409))
      }
      "Returns 403 if the bike ID is hired by someone else" in {
        POST("/station/12345/bike/001") {
          """
            {
              "action": "return",
              "username": "pedalpushr"
            }
          """
        } should have ('code (403))
      }
    }
  }

  """
    |AS a maintenance engineer
    |I WANT to know which bikes to re-locate
    |SO THAT bikes are available at all stations
  """.stripMargin - {
    "DELETE /station/all" - {
      "Remove all stations" in {
        DELETE("/station/all") should have ('code (200))
      }
    }
    "GET /station/depleted" - {
      "List locations with low number of bikes, together with near locations that have many bikes (ie. candidates for balancing)" in {
        PUT("/station/2001") {"""{
                                    "name": "Loadsa Bikes",
                                    "location": {
                                      "lat": 2.16,
                                      "long": 45.4
                                    },
                                    "availableBikes": [
                                      "100","101","102","103","104","105","106","107","108","109",
                                      "110","111","112","113","114","115","116","117","118","119",
                                      "120","121","122","123","124","125","126","127","128","129"
                                    ]
                                  }"""}
        PUT("/station/2002") {"""{
                                    "name": "Few bikes",
                                    "location": {
                                      "lat": 2.15,
                                      "long": 45.4
                                    },
                                    "availableBikes": [
                                      "190","191","192"
                                    ]
                                  }"""}
        GET("/station/depleted").body should be (
          """
            {
              "items": [
                {
                  "stationUrl": "/station/2002",
                  "availableBikes": 3,
                  "nearbyFullStations" : [
                    {
                      "stationUrl": "/station/2001",
                      "availableBikes": 30
                    }
                  ]
                }
              ]
            }
          """.parseJson)
      }
      "Not list locations with more than 10% of all available bikes" in pending
    }
  }

  def GET(url: String): HttpResponse[JsObject] = {
    info(s"GET $url")
    val request =
      if (url.startsWith("http")) Http(url)
      else Http("http://localhost:9000" + url)
    request.execute(parseBodyAsJson)
  }

  def DELETE(url: String): HttpResponse[JsObject] = {
    info(s"DELETE $url")
    Http("http://localhost:9000" + url)
      .method("DELETE")
      .execute(parseBodyAsJson)
  }

  def PUT(url: String)(json: String): HttpResponse[JsObject] = {
    info(s"PUT $url ${json.parseJson}")
    Http("http://localhost:9000" + url)
      .postData(json)
      .header("Content-Type", "application/json")
      .method("PUT")
      .execute(parseBodyAsJson)
  }

  def POST(url: String)(json: String): HttpResponse[JsObject] = {
    info(s"POST $url ${json.parseJson}")
    Http("http://localhost:9000" + url)
      .postData(json)
      .header("Content-Type", "application/json")
      .method("POST")
      .execute(parseBodyAsJson)
  }

  def parseBodyAsJson(is: InputStream): JsObject =
    Try(HttpConstants.readString(is))
      .map {
        case "" => JsObject()
        case other => other.parseJson.asJsObject
      } match {
        case Failure(exception) => fail(s"Could not parse JSON from response: ${exception.getMessage}")
        case Success(json) => json
      }

  def tupleOfUrls(body: JsValue): (String, String) = body.asJsObject.getFields("selfUrl", "hireUrl") match {
    case Seq(JsString(selfUrl), JsString(hireUrl)) => (selfUrl, hireUrl)
  }

  val Port = 9005
  val Host = "localhost"
  val wireMockServer = new WireMockServer(wireMockConfig().port(Port))

  override def beforeAll {
    wireMockServer.start()
    WireMock.configureFor(Host, Port)
  }

  override def afterAll {
    wireMockServer.stop()
  }
}
