
import java.io.InputStream
import com.github.tomakehurst.wiremock._
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalactic.Fail
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.{MatchResult, Matcher}

import scala.util.{Failure, Success, Try}
import scalaj.http._

import spray.json._
import DefaultJsonProtocol._

class RESTSpec extends FreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  val Port = 9005
  val Host = "localhost"
  val wireMockServer = new WireMockServer(wireMockConfig().port(Port))
  setupCustomerStub()

  "DELETE /station/all" - {
    "Remove all stations" - {
      DELETE("/station/all") `should respond with` `status code` (200)
    }
  }

  """
    |AS a maintenance engineer
    |I WANT to add a location and set the bikes that it has
    |SO THAT bikes can be hired
  """.stripMargin - {
    "PUT /station/<id>" - {
      "Create a station with a set of bike IDs" - {
        PUT("/station/12345")("""{
                                    "name": "West Road",
                                    "location": {
                                      "lat": 3.20,
                                      "long": 40.24
                                    },
                                    "availableBikes": [
                                      "001","002","003","004"
                                    ]
                                  }""") `should respond with` (
          (`status code` (200) or `status code` (201)) and `Location header`("/station/12345")
        )

        GET("/station/12345") `should respond with` (`status code`(200) and body("""{
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
      "Update an existing station with new details" - {
        PUT("/station/12345")("""{
                                   "name": "South Road",
                                   "location": {
                                     "lat": 3.23,
                                     "long": 40.21
                                   },
                                   "availableBikes": [
                                     "001","003","005"
                                   ]
                                 }""") `should respond with` (`status code`(200) or `status code` (201))
        GET("/station/12345") `should respond with` (`status code`(200) and body("""{
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
      "Remove given bike IDs from other stations" - {
        PUT("/station/67890")("""{
                                   "name": "East Road",
                                   "location": {
                                     "lat": 3.03,
                                     "long": 40.01
                                   },
                                   "availableBikes": [
                                     "005"
                                   ]
                                 }""") `should respond with` (`status code`(200) or `status code` (201))
        GET("/station/12345") `should respond with` (`status code`(200) and bodyProperty("availableBikes", """["001","003"]"""))
        GET("/station/67890") `should respond with` (`status code`(200) and bodyProperty("availableBikes", """["005"]"""))
      }
    }
  }

  """
    |AS a customer
    |I WANT to hire a bike near to me, and return it to a different location
    |SO THAT I can get somewhere
  """.stripMargin - {
    "GET /station/near/<lat>/<long>" - {
      "List locations within 0.01 deg of current location, with count of bikes per station" - {
        GET("/station/12345") `should respond with` (`status code`(200) and body("""{
                                                           "name": "South Road",
                                                           "location": {
                                                             "lat": 3.23,
                                                             "long": 40.21
                                                           },
                                                           "availableBikes": [
                                                             "001","003"
                                                           ]
                                                         }"""))
        GET("/station/67890") `should respond with` (`status code`(200) and body("""{
                                                           "name": "East Road",
                                                           "location": {
                                                             "lat": 3.03,
                                                             "long": 40.01
                                                           },
                                                           "availableBikes": [
                                                             "005"
                                                           ]
                                                         }"""))
        GET("/station/near/3.04/40.02") `should respond with` (`status code`(200) and body(
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
          """))
        PUT("/station/1000001")("""{
                                   "name": "First Avenue",
                                   "location": {
                                     "lat": 3.05,
                                     "long": 40.01
                                   },
                                   "availableBikes": [
                                     "006", "007", "008", "009", "010"
                                   ]
                                 }""")  `should respond with` `status code`(200)
        GET("/station/near/3.04/40.019999999") `should respond with` (`status code`(200) and body(
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
          """))
      }
      "Return an empty listing when there are no stations within 0.01 deg of current location" - {
        GET("/station/near/1.00/5.00") `should respond with` (`status code`(200) and body("""{ "items": [] }"""))
      }
    }
    "POST /station/<station_id>/bike" - {
      "Requests hire of a bike and returns a bike ID" - {
        POST("/station/1000001/bike")("""{ "action": "hire", "username": "badrida382" }""") `should respond with` (
          `status code` (200) and
          body ("""{ "bikeId": "006" }""")
        )
        GET("/station/1000001") `should respond with` (
          `status code` (200) and
          bodyProperty("availableBikes", """["007","008","009","010"]""")
        )
      }
      "Returns bike IDs in order" - {
        GET("/station/12345") `should respond with` (`status code`(200) and bodyProperty("availableBikes", """["001","003"]"""))
        POST("/station/12345/bike", attempt = 1)(
          """
            {
              "action": "hire",
              "username": "badrida382"
            }
          """
        ) `should respond with` (`status code`(200) and body ("""{ "bikeId": "001" }"""))
        POST("/station/12345/bike", attempt = 2) {
          """
            {
              "action": "hire",
              "username": "badrida382"
            }
          """
        } `should respond with` (`status code`(200) and body ( """{ "bikeId": "003" }"""))
      }
      "Returns 404 if no bikes are available" - {
        GET("/station/12345") `should respond with` (`status code`(200) and bodyProperty ("availableBikes", """[]"""))
        POST("/station/12345/bike") {
          """
            {
              "action": "hire",
              "username": "badrida382"
            }
          """
        } `should respond with` (`status code` (404) or `status code` (204))
      }
      "Returns 401 if GET /customer/<username> indicates that the customer is unauthorised" - {
        GET("/station/67890") `should respond with` (`status code`(200) and bodyProperty("availableBikes", """["005"]"""))
        POST("/station/67890/bike") {
          """
            {
              "action": "hire",
              "username": "i_am_not_authorised"
            }
          """
        } `should respond with` `status code`(401)
      }
    }
    "POST /station/<station_id>/bike/<bike_id>" - {
      "Notifies hire completed for a bike that is currently hired out" - {
        POST("/station/12345/bike/006") {
          """
            {
              "action": "return",
              "username": "badrida382"
            }
          """
        } `should respond with` `status code`(200)
        GET("/station/12345") `should respond with` (`status code`(200) and bodyProperty("availableBikes", """["006"]"""))
      }
      "Returns 404 if the bike ID is unknown" - {
        POST("/station/12345/bike/999") {
          """
            {
              "action": "return",
              "username": "badrida382"
            }
          """
        } `should respond with` `status code` (404)
      }
      "Returns 409 if the bike ID is not currently hired" - {
        POST("/station/12345/bike/008") {
          """
            {
              "action": "return",
              "username": "badrida382"
            }
          """
        } `should respond with` `status code` (409)
      }
      "Returns 403 if the bike ID is hired by someone else" - {
        POST("/station/12345/bike/001") {
          """
            {
              "action": "return",
              "username": "pedalpushr"
            }
          """
        } `should respond with` `status code` (403)
      }
    }
  }

  """
    |AS a maintenance engineer
    |I WANT to know which bikes to re-locate
    |SO THAT bikes are available at all stations
  """.stripMargin - {
    "DELETE /station/all" - {
      "Remove all stations" - {
        DELETE("/station/all") `should respond with` `status code` (200)
      }
    }
    "GET /station/depleted" - {
      "List locations with low number of bikes, together with near locations that have many bikes (ie. candidates for balancing)" - {
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
                                  }"""}  `should respond with` `status code`(200)
        PUT("/station/2002") {"""{
                                    "name": "Few bikes",
                                    "location": {
                                      "lat": 2.15,
                                      "long": 45.4
                                    },
                                    "availableBikes": [
                                      "190","191","192"
                                    ]
                                  }"""}  `should respond with` `status code`(200)
        GET("/station/depleted") `should respond with` (`status code`(200) and body (
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
          """))
      }
      "Not list locations with more than 10% of all available bikes" in pending
    }
  }

  def GET_old(url: String): HttpResponse[JsObject] = {
    info(s"GET $url")
    val request =
      if (url.startsWith("http")) Http(url)
      else Http("http://localhost:9000" + url)
    request.execute(parseBodyAsJson)
  }

  def GET(url: String, attempt: Int = -1) = TestStep(s"GET", attempt)(url)(None)
  def DELETE(url: String, attempt: Int = -1) = TestStep(s"DELETE", attempt)(url)(None)
  def PUT(url: String, attempt: Int = -1)(requestBody: String) = TestStep(s"PUT", attempt)(url)(Some(requestBody))
  def POST(url: String, attempt: Int = -1)(requestBody: String) = TestStep(s"POST", attempt)(url)(Some(requestBody))

  case class TestStep(method: String, attempt: Int)(url: String)(reqBody: Option[String]) {
    def req = {
      val plain = Http("http://localhost:9000" + url)
      reqBody match {
        case Some(r) => plain.postData(r).method(method).header("Content-Type", "application/json")
        case None => plain.method(method)
      }
    }

    def `should respond with`(matcher: Matcher[HttpResponse[String]]) = {
      val response = req.asString
      val attemptSuffix = attempt match {
        case -1 => ""
        case other => s" - Attempt $other"
      }
      s"$method $url$attemptSuffix" in { response should matcher }
      response
    }
  }

  def PUT_old(url: String)(json: String): HttpResponse[JsObject] = {
    info(s"PUT $url ${json.parseJson}")
    Http("http://localhost:9000" + url)
      .postData(json)
      .header("Content-Type", "application/json")
      .method("PUT")
      .execute(parseBodyAsJson)
  }

  def POST_old(url: String)(json: String): HttpResponse[JsObject] = {
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

  def `status code`(expected: Int) = Matcher[HttpResponse[_]] { case r@ HttpResponse(_, actual, _) =>
    MatchResult(
      matches = actual == expected,
      rawFailureMessage = s"Response status code $actual was not $expected",
      rawNegatedFailureMessage = s"Response status code was $expected"
    )
  }

  def `Location header`(expected: String) = Matcher[HttpResponse[_]] { case r@ HttpResponse(_, _, headers) =>
    headers.get("Location") match {
      case None => fail(s"Response $r did not have a Location header")
      case Some(actual) => MatchResult(
        matches = actual endsWith expected,
        rawFailureMessage = s"Response Location header '$actual' did not end with '$expected'",
        rawNegatedFailureMessage = s"Response Location header ended with '$expected'"
      )
    }
  }

  def body(expected: String) = Matcher[HttpResponse[String]] { case r@ HttpResponse(actual, _, _) =>
    val expectedJ = expected.parseJson
    actual match {
      case "" => MatchResult(false, s"Response body was empty, expected ${expectedJ.prettyPrint}", "")
      case other => Try(other.parseJson.asJsObject) match {
        case Failure(_) => MatchResult(false, s"Response body '${other.take(20)}...' was not JSON", "")
        case Success(actualJ) => MatchResult(
          matches = actualJ == expectedJ,
          rawFailureMessage = s"Response body $actualJ was not ${expectedJ.prettyPrint}",
          rawNegatedFailureMessage = s"Response body was not ${expectedJ.prettyPrint}"
        )
      }
    }
  }

  def bodyProperty(name: String, expected: String) = Matcher[HttpResponse[String]] { case r@ HttpResponse(actual, _, _) =>
    val expectedJ = expected.parseJson
    actual match {
      case "" => MatchResult(false, s"Response body was empty, not ${expectedJ.prettyPrint} at property $name", "")
      case other => Try(other.parseJson.asJsObject) match {
        case Failure(_) => MatchResult(false, s"Response body '${other.take(20)}...' was not JSON, expected ${expectedJ.prettyPrint} at property $name", "")
        case Success(actualJ) => MatchResult(
          matches = actualJ.fields(name) == expectedJ,
          rawFailureMessage = s"Response body $actualJ was not ${expectedJ.prettyPrint} at property $name",
          rawNegatedFailureMessage = s"negated matching not supported"
        )
      }
    }
  }

  def setupCustomerStub(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(Host, Port)
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
        )
    )
    stubFor(
      get(urlEqualTo("/customer/i_am_not_authorised"))
        .
        willReturn(
          aResponse()
            .withStatus(401)
        )
    )
  }

  override def afterAll {
    wireMockServer.stop()
  }
}
