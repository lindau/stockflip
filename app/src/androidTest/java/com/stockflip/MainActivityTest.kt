package com.stockflip

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.hamcrest.CoreMatchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun searchInput_isDisplayed() {
        onView(withId(R.id.searchInput))
            .check(matches(isDisplayed()))
    }

    @Test
    fun searchInput_initiallyEmpty() {
        onView(withId(R.id.searchInput))
            .check(matches(withText("")))
    }

    @Test
    fun searchInput_canEnterText() {
        val searchText = "volvo"
        onView(withId(R.id.searchInput))
            .perform(typeText(searchText))
            .check(matches(withText(searchText)))
    }

    @Test
    fun dropDown_initiallyNotVisible() {
        onView(withId(R.id.searchInput))
            .check(matches(not(hasDropDownItems())))
    }

    @Test
    fun dropDown_showsForValidSearch() {
        onView(withId(R.id.searchInput))
            .perform(typeText("volvo"))
            .perform(waitFor(1000)) // Wait for search results
            .check(matches(hasDropDownItems()))
    }

    @Test
    fun dropDown_notShownForSingleCharacter() {
        onView(withId(R.id.searchInput))
            .perform(typeText("v"))
            .perform(waitFor(1000)) // Wait for potential results
            .check(matches(not(hasDropDownItems())))
    }

    @Test
    fun dropDown_notShownForEmptyInput() {
        onView(withId(R.id.searchInput))
            .perform(typeText("volvo"))
            .perform(waitFor(1000)) // Wait for search results
            .perform(clearText())
            .perform(waitFor(1000)) // Wait for UI update
            .check(matches(not(hasDropDownItems())))
    }

    @Test
    fun dropDown_showsSwedishStocksFirst() {
        onView(withId(R.id.searchInput))
            .perform(typeText("volvo"))
            .perform(waitFor(1000)) // Wait for search results
            .check(matches(withFirstDropDownItem(containsString("Stockholmsbörsen"))))
    }

    @Test
    fun dropDown_dismissedOnFocusLoss() {
        onView(withId(R.id.searchInput))
            .perform(typeText("volvo"))
            .perform(waitFor(1000)) // Wait for search results
            .perform(clearFocus())
            .check(matches(not(hasDropDownItems())))
    }

    @Test
    fun dropDown_showsLoadingState() {
        onView(withId(R.id.searchInput))
            .perform(typeText("volvo"))
            .check(matches(hasLoadingIndicator()))
    }

    @Test
    fun dropDown_handlesNoResults() {
        onView(withId(R.id.searchInput))
            .perform(typeText("nonexistentstock"))
            .perform(waitFor(1000)) // Wait for search results
            .check(matches(not(hasDropDownItems())))
    }

    private fun waitFor(millis: Long) = object : ViewAction {
        override fun getConstraints() = isDisplayed()
        override fun getDescription() = "Wait for $millis milliseconds"
        override fun perform(uiController: UiController, view: View) {
            uiController.loopMainThreadForAtLeast(millis)
        }
    }

    private fun hasDropDownItems() = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("has dropdown items")
        }

        override fun matchesSafely(item: View): Boolean {
            return (item as? AutoCompleteTextView)?.adapter?.count ?: 0 > 0
        }
    }

    private fun hasLoadingIndicator() = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("has loading indicator")
        }

        override fun matchesSafely(item: View): Boolean {
            return (item as? AutoCompleteTextView)?.isPerformingCompletion ?: false
        }
    }

    private fun withFirstDropDownItem(matcher: Matcher<String>) = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("with first dropdown item: ")
            matcher.describeTo(description)
        }

        override fun matchesSafely(item: View): Boolean {
            val adapter = (item as? AutoCompleteTextView)?.adapter ?: return false
            if (adapter.count == 0) return false
            val firstItem = adapter.getItem(0).toString()
            return matcher.matches(firstItem)
        }
    }
} 