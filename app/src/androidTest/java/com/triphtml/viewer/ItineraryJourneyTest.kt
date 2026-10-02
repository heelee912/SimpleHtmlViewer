package com.triphtml.viewer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real HTML template, isolated synthetic trip data and real Android input. */
@RunWith(AndroidJUnit4::class)
class ItineraryJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val probe = ViewerProbe().apply { scriptTimeoutSeconds = 20 }

    private fun check(name: String, body: () -> Unit) {
        try { body(); Log.i("ItineraryTest", "PASS $name") }
        catch (failure: AssertionError) {
            probe.capture("itinerary-failure.png")
            val diagnostic = probe.js("JSON.stringify({counter:document.getElementById('counter').textContent,hash:location.hash,errors:fixtureErrors,clicks:fixtureClicks.slice(-3),tap:window.fixtureTap,target:(()=>{const e=document.getElementById('itinerary-test-target');if(!e)return null;const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,over:document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.outerHTML.slice(0,200)}})()})")
            throw AssertionError("$name: ${failure.message}; diagnostics=$diagnostic")
        }
    }
    private fun jsTrue(script: String) {
        if (probe.js(script) != "true") {
            probe.capture("itinerary-failure.png")
            throw AssertionError("$script; diagnostics=${probe.js("JSON.stringify({counter:document.getElementById('counter').textContent,hash:location.hash,errors:fixtureErrors,clicks:fixtureClicks.slice(-3),tap:window.fixtureTap})")}")
        }
    }
    private fun tap(selector: String) {
        compose.waitForIdle()
        val escaped = JSONObject.quote(selector)
        probe.js("document.querySelectorAll('[id=\"itinerary-test-target\"]').forEach(e=>e.removeAttribute('id'));document.querySelector($escaped).id='itinerary-test-target';window.fixtureExpectedSelector=$escaped;window.fixtureExpectedClick=false;window.fixtureTap={selector:$escaped,scale:visualViewport.scale,width:innerWidth,dpr:devicePixelRatio};true")
        probe.tapHtml("itinerary-test-target")
        probe.await("Expected element did not receive a finger click: $selector") { probe.js("fixtureExpectedClick") == "true" }
    }
    private fun modal(open: Boolean) {
        try {
            probe.await("Modal state did not become $open") { probe.js("document.getElementById('modal').open") == open.toString() }
        } catch (failure: AssertionError) {
            probe.capture("itinerary-failure.png")
            throw AssertionError("${failure.message}; pageErrors=${probe.js("fixtureErrors")}; state=${probe.js("JSON.stringify({scroll:scrollY,focus:document.activeElement.outerHTML.slice(0,300),title:document.getElementById('mtitle').textContent})")}")
        }
    }
    private fun tools(open: Boolean) = compose.waitUntil(7_000) {
        (compose.onAllNodesWithTagCount("document_tools") > 0) == open
    }
    private fun choose(file: String, first: Boolean = false) {
        if (first) compose.onNodeWithTag("choose_document").performClick()
        else { probe.pressBack(); tools(true); compose.onNodeWithTag("open_file").performClick() }
        probe.pickFixture(file)
        probe.awaitReady()
        // WebView can finish behind the outgoing Compose welcome screen. Let the test frame clock
        // finish that transition before injecting a physical touch into the page beneath it.
        compose.waitForIdle()
        compose.waitUntil(8_000) { compose.onAllNodesWithTagCount("welcome") == 0 && compose.onAllNodesWithTagCount("recovery") == 0 }
        assertEquals("[]", probe.js("fixtureErrors"))
    }
    private fun openSchedule() { tap("#day1 [data-detail='EXAMPLE-ride-1']"); modal(true) }
    private fun changedTrip(edit: String, verify: () -> Unit) {
        // Only generated test assets expose fixtureTrip. The delivered HTML has no fixture hooks.
        probe.js("window.fixtureTripBefore=JSON.stringify(fixtureTrip);$edit;fixtureRender();true")
        try { verify() }
        finally {
            probe.js("if(document.getElementById('modal').open)document.getElementById('modal').close();Object.keys(fixtureTrip).forEach(k=>delete fixtureTrip[k]);Object.assign(fixtureTrip,JSON.parse(fixtureTripBefore));fixtureRender();true")
        }
    }
    private fun savedChoices() {
        jsTrue("document.querySelector('#day1 [data-done=\"EXAMPLE-ride-1\"]').getAttribute('aria-pressed')==='true'")
        jsTrue("document.querySelector('#day1 [data-mode=\"better\"]').getAttribute('aria-pressed')==='true'")
    }

    @Test fun timetableAndChoicesSurviveTheReadingJourney() {
        probe.forgetDocument()
        probe.launch().use { scenario ->
            choose("itinerary-five.html", first = true)
            probe.js("window.fixtureReady=false;localStorage.clear();location.reload();true")
            probe.awaitReady()
            check("five data days generate exactly five tabs and clamp navigation") {
                assertEquals("5", probe.js("document.querySelectorAll('.date-tabs [data-go]').length"))
                tap(".date-tabs [data-go='5']")
                jsTrue("document.getElementById('next').disabled&&document.getElementById('counter').textContent==='05 / 5'")
                tap(".date-tabs [data-go='1']")
            }
            check("the existing schedule button opens every alternative and both last-service concepts") {
                openSchedule()
                assertEquals("9", probe.js("document.querySelectorAll('#mbody [data-schedule-row]').length"))
                jsTrue("document.getElementById('mbody').innerText.includes('11:40 출발')&&document.getElementById('mbody').innerText.includes('18:00 출발')")
                jsTrue("document.querySelector('[data-schedule-row=\"EXAMPLE-F\"]').innerText.includes('12:50 환승 버스를 놓쳐')")
                jsTrue("document.getElementById('mbody').innerText.includes('13:40')&&document.getElementById('mbody').innerText.includes('14:10')&&document.getElementById('mbody').innerText.includes('16:00')")
                probe.capture("itinerary-modal-top.png")
            }
            check("modal body scrolls to sources while close button stays visible") {
                Log.i("ItineraryTest", "Modal geometry " + probe.js("JSON.stringify({dialog:document.getElementById('modal').getBoundingClientRect().height,body:document.getElementById('mbody').clientHeight,content:document.getElementById('mbody').scrollHeight,viewport:innerHeight})"))
                val box = JSONObject(probe.js("(()=>{const r=document.getElementById('mbody').getBoundingClientRect();return {x:r.x+r.width/2,top:r.top,bottom:r.bottom,scale:devicePixelRatio};})()"))
                val scale = box.getDouble("scale")
                val origin = probe.onMain { IntArray(2).also(probe.web()::getLocationOnScreen) }
                val x = (origin[0] + box.getDouble("x") * scale).toInt()
                val top = (origin[1] + (box.getDouble("top") + 25) * scale).toInt()
                val bottom = (origin[1] + (box.getDouble("bottom") - 25) * scale).toInt()
                probe.device.swipe(x, bottom, x, top, 25)
                probe.await("Modal did not scroll from touch") { probe.js("document.getElementById('mbody').scrollTop>0") == "true" }
                probe.js("document.getElementById('mbody').scrollTop=document.getElementById('mbody').scrollHeight;true")
                jsTrue("(()=>{const b=document.getElementById('mbody');return b.scrollTop+b.clientHeight>=b.scrollHeight-2})()")
                jsTrue("(()=>{const r=document.querySelector('.close-dialog').getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight})()")
                jsTrue("document.querySelector('.schedule-sources').innerText.includes('2000-01-01')")
                probe.capture("itinerary-modal-bottom.png")
            }
            check("Maps receives a user tap and return keeps the open timetable") {
                probe.js("window.itineraryLiveProof=73;true")
                tap("#mbody a[href*='google.com/maps']")
                probe.await("Maps did not become foreground", 15_000) { probe.foregroundPackage() == "com.google.android.apps.maps" }
                probe.relaunchToFront()
                modal(true)
                assertEquals("73", probe.js("window.itineraryLiveProof"))
                assertEquals("9", probe.js("document.querySelectorAll('#mbody [data-schedule-row]').length"))
            }
            check("rotation keeps the modal and its scroll area usable") {
                val activity = requireNotNull(probe.activity)
                try {
                    probe.instrumentation.runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    probe.await("Landscape not applied") { activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                    SystemClock.sleep(500)
                    modal(true)
                    assertEquals("73", probe.js("window.itineraryLiveProof"))
                    jsTrue("(()=>{const r=document.getElementById('mbody').getBoundingClientRect();return r.height>40&&r.bottom<=innerHeight+1})()")
                    jsTrue("(()=>{const r=document.querySelector('.close-dialog').getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight})()")
                    probe.capture("itinerary-modal-landscape.png")
                } finally {
                    probe.instrumentation.runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    probe.await("Portrait not restored") { activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
                }
            }
            check("Back closes the HTML modal before opening app tools") {
                probe.pressBack(); modal(false); tools(false)
                assertFalse(probe.js("document.body.classList.contains('dialog-open')") == "true")
                probe.pressBack(); tools(true)
                compose.onNodeWithTag("resume_reading").performClick(); tools(false)
            }
            check("a changed actual body time suppresses every safe claim in the modal") {
                changedTrip("fixtureTrip.days[0].base.find(s=>s.id==='EXAMPLE-visit-1').start='11:15'") {
                    openSchedule()
                    jsTrue("document.querySelector('.schedule-limit').innerText.includes('최후 안전편 미확인')")
                    jsTrue("document.querySelector('.schedule-warning').innerText.includes('본문 시작 시각과 연결 동선이 다릅니다')")
                    assertEquals("0", probe.js("document.querySelectorAll('[data-schedule-status=\"safe\"]').length"))
                    probe.capture("itinerary-unlinked-body.png")
                }
            }
            for ((reservation, warning) in listOf("unbooked" to "미예약", "sold-out" to "매진")) {
                check("$reservation does not erase the known operating last departure") {
                    changedTrip("fixtureTrip.timetables['EXAMPLE-board-1'].rows.at(-1).operation.reservation='$reservation'") {
                        openSchedule()
                        jsTrue("document.querySelector('.schedule-limit').innerText.includes('노선의 실제 막차 18:00')")
                        jsTrue("document.querySelector('.schedule-limit').innerText.includes('11:40')")
                        jsTrue("document.querySelector('[data-schedule-row=\"EXAMPLE-E\"]').dataset.scheduleStatus==='safe'")
                        jsTrue("!document.querySelector('#mbody').innerText.includes('막차로 표시된 편이 이용일에 운행하지 않습니다')")
                        jsTrue("document.querySelector('[data-schedule-row=\"EXAMPLE-LAST\"]').innerText.includes('$warning')")
                        jsTrue("document.querySelector('[data-schedule-row=\"EXAMPLE-LAST\"]').dataset.scheduleStatus!=='safe'")
                    }
                }
            }
            for ((field, value, warning) in listOf(
                Triple("status", "cancelled", "운휴·취소"), Triple("status", "not-running", "해당 날짜 미운행"),
                Triple("reservation", "unbooked", "필수 예약이 아직 없어"), Triple("reservation", "sold-out", "매진"),
            )) {
                check("fixed connection $value is named with its blocking reason") {
                    changedTrip("fixtureTrip.timetables['EXAMPLE-board-1'].onward.find(s=>s.kind==='connection').operation.$field='$value'") {
                        openSchedule()
                        jsTrue("document.querySelector('.schedule-warning').innerText.includes('다음 고정 버스')&&document.querySelector('.schedule-warning').innerText.includes('$warning')")
                        jsTrue("document.querySelector('.schedule-limit').innerText.includes('최후 안전편 미확인')")
                        val chainWarning = if (value == "unbooked") "미예약" else warning
                        jsTrue("document.querySelector('.schedule-chain').innerText.includes('$chainWarning')")
                        if (value == "cancelled") probe.capture("itinerary-connection-cancelled.png")
                    }
                }
            }
            check("ordinary place detail includes local name and contact") {
                tap("#day1 [data-detail='EXAMPLE-visit-1']"); modal(true)
                jsTrue("document.getElementById('mbody').innerText.includes('架空会場')&&document.getElementById('mbody').innerText.includes('000-0000-0000')")
                tap(".close-dialog"); modal(false)
            }
            check("check marks, preparation checks and alternatives survive refresh") {
                tap("#day1 [data-done='EXAMPLE-ride-1']")
                tap("#day1 [data-mode='better']")
                tap("#day1 [data-prep='1']"); modal(true)
                tap("[data-prep-check='1-0']"); tap(".close-dialog"); modal(false)
                savedChoices()
                probe.js("window.fixtureReady=false;true")
                probe.pressBack(); tools(true); compose.onNodeWithTag("refresh").performClick()
                probe.awaitReady(); savedChoices()
                tap("#day1 [data-prep='1']"); modal(true)
                jsTrue("document.querySelector('[data-prep-check=\"1-0\"]').checked")
                probe.pressBack(); modal(false)
            }
            check("a different eight-day file cannot inherit choices even with a copied document ID") {
                choose("itinerary-eight.html")
                assertEquals("8", probe.js("document.querySelectorAll('.date-tabs [data-go]').length"))
                jsTrue("document.querySelector('#day1 [data-done=\"EXAMPLE-ride-1\"]').getAttribute('aria-pressed')==='false'")
                jsTrue("document.querySelector('#day1 [data-mode=\"base\"]').getAttribute('aria-pressed')==='true'")
                tap(".date-tabs [data-go='8']")
                jsTrue("document.getElementById('next').disabled&&document.getElementById('counter').textContent==='08 / 8'")
                probe.capture("itinerary-eight-days.png")
            }
            check("reselecting the first file and recreating Activity retains its own choices") {
                choose("itinerary-five.html"); savedChoices()
                probe.recreate(scenario); probe.awaitReady(); savedChoices()
                openSchedule(); modal(true)
                jsTrue("document.getElementById('mbody').innerText.includes('최후 안전편')")
                probe.pressBack(); modal(false)
                assertEquals("[]", probe.js("fixtureErrors"))
                probe.js("localStorage.setItem('itinerary-proof','durable');true")
            }
            check("author contract and example are never rendered by the blank template") {
                choose("itinerary-template.html")
                jsTrue("!document.body.innerText.includes('EXAMPLE-')&&!document.body.innerText.includes('AUTHOR_ONLY')&&!document.body.innerText.includes('작성 계약')")
                jsTrue("[...document.querySelectorAll('[aria-label],[title]')].every(e=>!/(AUTHOR_ONLY|EXAMPLE-|작성 계약)/.test((e.getAttribute('aria-label')||'')+(e.title||'')))")
                probe.js("(()=>{const d=JSON.parse(document.getElementById('trip-data').textContent);const s=d.days[0].base.find(s=>d.events[s.id].serviceId);document.querySelector('[data-detail=\"'+s.id+'\"]').id='blank-timetable';})()")
                probe.tapHtml("blank-timetable"); modal(true)
                jsTrue("!document.getElementById('mbody').innerText.includes('EXAMPLE-')&&!document.getElementById('mbody').innerText.includes('작성')")
                probe.pressBack(); modal(false); tools(false)
                choose("itinerary-five.html"); savedChoices()
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class ItineraryRelaunchTest {
    private val probe = ViewerProbe()
    @Test fun processRelaunchRetainsTheTripAndItsChoices() {
        probe.launch().use {
            probe.awaitReady()
            assertEquals("\"durable\"", probe.js("localStorage.getItem('itinerary-proof')"))
            assertEquals("5", probe.js("document.querySelectorAll('.date-tabs [data-go]').length"))
            assertEquals("true", probe.js("document.querySelector('#day1 [data-done=\"EXAMPLE-ride-1\"]').getAttribute('aria-pressed')==='true'"))
            assertEquals("true", probe.js("document.querySelector('#day1 [data-mode=\"better\"]').getAttribute('aria-pressed')==='true'"))
            assertEquals(1, probe.instrumentation.targetContext.contentResolver.persistedUriPermissions.size)
        }
    }
}
