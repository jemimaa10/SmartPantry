package com.example.mts

import com.example.mts.data.ApiErrorBody
import com.example.mts.data.AppJson
import com.example.mts.data.CatalogIngredient
import com.example.mts.data.Category
import com.example.mts.data.ExpiringGroups
import com.example.mts.data.ExpiryStatus
import com.example.mts.data.Favorite
import com.example.mts.data.FavoriteCreated
import com.example.mts.data.FindResult
import com.example.mts.data.GenerateResponse
import com.example.mts.data.MealPlanItem
import com.example.mts.data.MealWeek
import com.example.mts.data.PantryItem
import com.example.mts.data.RecipeCollection
import com.example.mts.data.RecipeDetail
import com.example.mts.data.RecipeSummary
import com.example.mts.data.ScanHistoryEntry
import com.example.mts.data.ScanResponse
import com.example.mts.data.Session
import com.example.mts.data.ShoppingItem
import com.example.mts.data.ShoppingList
import com.example.mts.data.SubstituteResponse
import com.example.mts.data.UserMe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decodes responses recorded by backend/tests/contract.test.ts. */
class ContractTest {
    private inline fun <reified T> fixture(name: String): T {
        val text = checkNotNull(javaClass.classLoader?.getResource("contract/$name.json")) {
            "Missing fixture $name; run the backend tests"
        }.readText()
        return AppJson.decodeFromString(text)
    }

    @Test
    fun auth() {
        assertTrue(fixture<Session>("session").accessToken.isNotEmpty())
        assertEquals("Email atau kata sandi salah.", fixture<ApiErrorBody>("error").error?.message)
    }

    @Test
    fun user() {
        val me = fixture<UserMe>("me")
        assertEquals("Kontrak", me.displayName)
        assertTrue(me.hasPassword)
        assertEquals(3, me.notifications.expiryDaysBefore)
        assertEquals(listOf("udang"), fixture<UserMe>("preferences").allergies)
    }

    @Test
    fun catalog() {
        assertEquals(10, fixture<List<Category>>("categories").size)
        assertTrue(fixture<List<CatalogIngredient>>("ingredients").any { it.nameEn == "shallot" })
    }

    @Test
    fun pantry() {
        val item = fixture<PantryItem>("pantry_item")
        assertEquals("spinach", item.nameEn)
        assertEquals(6.0, item.quantity!!, 0.0)
        assertEquals(ExpiryStatus.EXPIRED, item.expiryStatus)
        assertEquals("2020-01-01", item.expiresAt)
        assertEquals(2, fixture<List<PantryItem>>("pantry_bulk").size)
        assertEquals(3, fixture<List<PantryItem>>("pantry").size)
        assertEquals(1, fixture<ExpiringGroups>("expiring").expired.size)
    }

    @Test
    fun recipeSearch() {
        val found = fixture<FindResult>("find_result")
        val hit = found.recipes.first { it.key == "spoonacular-1001" }
        assertEquals("ok", found.sources.spoonacular)
        assertEquals(50, hit.matchPercent)
        assertEquals(listOf("rice"), hit.missing)
        assertEquals(listOf("parsley"), hit.optionalMissing)
        assertTrue(fixture<FindResult>("search_result").recipes.isNotEmpty())
        assertTrue(fixture<List<RecipeSummary>>("recently_viewed").isNotEmpty())
    }

    @Test
    fun recipeDetail() {
        val recipe = fixture<RecipeDetail>("recipe_detail")
        assertEquals("spoonacular-1001", recipe.key)
        assertEquals(210.0, recipe.nutrition?.calories!!, 0.0)
        assertNull(recipe.nutrition?.protein)
        assertTrue(recipe.ingredients.first { it.name == "egg" }.available)
        assertFalse(recipe.ingredients.first { it.name == "parsley" }.required)
        assertEquals(300, recipe.instructions.first().timerSeconds)
        assertNull(recipe.favoriteId)
        assertEquals(50, recipe.match.matchPercent)
    }

    @Test
    fun ai() {
        val scan = fixture<ScanResponse>("scan")
        assertEquals("Telur", scan.ingredients.single().name_id)
        assertEquals("Egg", fixture<List<ScanHistoryEntry>>("history").single().ingredients.single().name)

        val recipe = fixture<GenerateResponse>("generate").recipes.single()
        assertTrue(recipe.isAiGenerated)
        assertEquals("Telur", recipe.ingredients.single().displayName)
        assertNull(recipe.nutrition)

        val substitution = fixture<SubstituteResponse>("substitute").substitutions.single()
        assertNull(substitution.substitute)
        assertEquals("not_recommended", substitution.suitability)
    }

    @Test
    fun favorites() {
        assertEquals("Malam", fixture<RecipeCollection>("collection").name)
        assertEquals(1, fixture<List<RecipeCollection>>("collections").single().recipeCount)
        assertTrue(fixture<FavoriteCreated>("favorite_created").id.isNotEmpty())
        val favorite = fixture<List<Favorite>>("favorites").single()
        assertEquals("Malam", favorite.collectionName)
        assertEquals("mealdb-52772", favorite.recipe.key)
    }

    @Test
    fun shopping() {
        val list = fixture<ShoppingList>("shopping_list")
        assertEquals("Beras", list.items.single().name)
        assertEquals("Beras & Biji-bijian", list.items.single().category)
        assertEquals(1, fixture<List<ShoppingList>>("shopping_lists").size)
        val updated = fixture<ShoppingItem>("shopping_item")
        assertTrue(updated.isPurchased)
        assertNull(updated.quantity)
    }

    @Test
    fun mealPlan() {
        assertEquals("DINNER", fixture<MealPlanItem>("meal_item").mealType)
        val week = fixture<MealWeek>("meal_week")
        assertEquals("2030-07-01", week.weekStart)
        assertEquals("mealdb-52772", week.items.single().recipe.key)
    }
}
