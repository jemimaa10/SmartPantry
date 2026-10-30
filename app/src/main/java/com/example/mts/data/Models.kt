package com.example.mts.data

import kotlinx.serialization.Serializable

@Serializable
data class Session(val accessToken: String, val refreshToken: String, val expiresIn: Int = 0)

@Serializable data class RegisterRequest(val name: String, val email: String, val password: String)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class GoogleRequest(val idToken: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class ForgotRequest(val email: String)
@Serializable data class ResetRequest(val token: String, val password: String)
@Serializable data class MessageResponse(val message: String)

@Serializable
data class NotificationPrefs(
    val expiryEnabled: Boolean = true,
    val expiryDaysBefore: Int = 3,
    val mealPlanEnabled: Boolean = false,
)

@Serializable
data class UserMe(
    val id: String,
    val email: String,
    val hasPassword: Boolean = false,
    val displayName: String,
    val avatarUrl: String? = null,
    val language: String = "id",
    val dietaryPreferences: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val dislikedIngredients: List<String> = emptyList(),
    val notifications: NotificationPrefs = NotificationPrefs(),
)

@Serializable data class UpdateProfile(val displayName: String)

@Serializable
data class UpdatePreferences(
    val dietaryPreferences: List<String>? = null,
    val allergies: List<String>? = null,
    val dislikedIngredients: List<String>? = null,
    val notifications: NotificationPrefs? = null,
)

@Serializable
data class Category(val slug: String, val nameId: String, val nameEn: String)

@Serializable
data class CatalogIngredient(
    val id: String,
    val nameEn: String,
    val nameId: String,
    val defaultUnit: String = "pcs",
    val category: Category,
)

object ExpiryStatus {
    const val FRESH = "FRESH"
    const val SOON = "EXPIRING_SOON"
    const val TODAY = "EXPIRING_TODAY"
    const val EXPIRED = "EXPIRED"
    const val NONE = "NONE"
}

@Serializable
data class PantryItem(
    val id: String,
    val ingredientId: String,
    val name: String,
    val nameEn: String,
    val category: Category,
    val quantity: Double? = null,
    val unit: String = "pcs",
    val expiresAt: String? = null,
    val expiryStatus: String = ExpiryStatus.NONE,
    val notes: String? = null,
)

@Serializable
data class PantryInput(
    val ingredientId: String? = null,
    val name: String? = null,
    val categorySlug: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val expiresAt: String? = null,
    val notes: String? = null,
)

@Serializable data class PantryBulk(val items: List<PantryInput>)

@Serializable
data class ExpiringGroups(
    val expiringSoon: List<PantryItem> = emptyList(),
    val expiringToday: List<PantryItem> = emptyList(),
    val expired: List<PantryItem> = emptyList(),
)

@Serializable
data class ScannedIngredient(
    val name: String,
    val name_id: String? = null,
    val confidence: Double = 0.0,
    val quantity: Double? = null,
    val unit: String? = null,
)

@Serializable
data class ScanResponse(
    val imageQuality: String,
    val ingredients: List<ScannedIngredient>,
    val message: String? = null,
)

@Serializable
data class ScanHistoryEntry(
    val id: String,
    val ingredients: List<ScannedIngredient> = emptyList(),
    val createdAt: String,
)

@Serializable
data class Nutrition(
    val calories: Double? = null,
    val protein: Double? = null,
    val carbohydrates: Double? = null,
    val fat: Double? = null,
)

@Serializable
data class RecipeSummary(
    val key: String,
    val source: String,
    val title: String,
    val imageUrl: String? = null,
    val cookingMinutes: Int? = null,
    val servings: Int? = null,
    val difficulty: String? = null,
    val nutrition: Nutrition? = null,
    val isAiGenerated: Boolean = false,
    val matchPercent: Int = 0,
    val available: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val optionalMissing: List<String> = emptyList(),
    val usesExpiring: List<String> = emptyList(),
)

@Serializable
data class SourceStatus(val spoonacular: String = "skipped", val themealdb: String = "skipped")

@Serializable
data class FindResult(
    val recipes: List<RecipeSummary> = emptyList(),
    val sources: SourceStatus = SourceStatus(),
    val suggestAi: Boolean = false,
)

@Serializable
data class RecipeIngredient(
    val name: String,
    val displayName: String? = null,
    val original: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val required: Boolean = true,
    val available: Boolean = false,
)

@Serializable
data class RecipeInstruction(val step: Int, val text: String, val timerSeconds: Int? = null)

@Serializable
data class RecipeMatch(
    val matchPercent: Int = 0,
    val available: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val optionalMissing: List<String> = emptyList(),
    val usesExpiring: List<String> = emptyList(),
)

@Serializable
data class RecipeDetail(
    val key: String,
    val source: String,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val cookingMinutes: Int? = null,
    val servings: Int? = null,
    val difficulty: String? = null,
    val nutrition: Nutrition? = null,
    val nutritionIsEstimate: Boolean = false,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val instructions: List<RecipeInstruction> = emptyList(),
    val isAiGenerated: Boolean = false,
    val favoriteId: String? = null,
    val match: RecipeMatch = RecipeMatch(),
)

@Serializable
data class GenerateRequest(
    val ingredients: List<String>? = null,
    val maxMinutes: Int? = null,
    val servings: Int? = null,
    val category: String? = null,
    val notes: String? = null,
    val count: Int = 1,
)

@Serializable data class GenerateResponse(val recipes: List<RecipeDetail>)

@Serializable
data class SubstituteRequest(val recipeTitle: String? = null, val missing: List<String>)

@Serializable
data class Substitution(
    val missing_ingredient: String,
    val substitute: String? = null,
    val how_to_substitute: String = "",
    val taste_texture_impact: String = "",
    val suitability: String = "acceptable",
    val safety_note: String? = null,
)

@Serializable data class SubstituteResponse(val substitutions: List<Substitution>)

@Serializable data class FavoriteRequest(val recipeKey: String, val collectionId: String? = null)
@Serializable data class FavoriteCreated(val id: String, val collectionId: String? = null)
@Serializable data class FavoriteMove(val collectionId: String?)

@Serializable
data class Favorite(
    val id: String,
    val collectionId: String? = null,
    val collectionName: String? = null,
    val recipe: RecipeSummary,
)

@Serializable data class RecipeCollection(val id: String, val name: String, val recipeCount: Int = 0)
@Serializable data class CollectionRequest(val name: String)

@Serializable
data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val category: String? = null,
    val isPurchased: Boolean = false,
    val recipeKey: String? = null,
)

@Serializable
data class ShoppingList(val id: String, val name: String, val items: List<ShoppingItem> = emptyList())

@Serializable
data class ShoppingItemInput(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val category: String? = null,
    val recipeKey: String? = null,
)

@Serializable data class ShoppingItemsRequest(val items: List<ShoppingItemInput>)
@Serializable data class ShoppingListRequest(val name: String)

@Serializable
data class MealRecipe(
    val key: String,
    val title: String,
    val imageUrl: String? = null,
    val cookingMinutes: Int? = null,
)

@Serializable
data class MealPlanItem(
    val id: String,
    val date: String,
    val mealType: String,
    val servings: Int? = null,
    val recipe: MealRecipe,
)

@Serializable
data class MealWeek(val weekStart: String, val weekEnd: String, val items: List<MealPlanItem> = emptyList())

@Serializable
data class MealPlanRequest(val recipeKey: String, val date: String, val mealType: String, val servings: Int? = null)

@Serializable
data class ApiErrorBody(val error: ApiErrorDetail? = null)

@Serializable
data class ApiErrorDetail(val code: String = "", val message: String = "")
