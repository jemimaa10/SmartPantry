package com.example.mts.data

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface Api {
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequest): Session
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): Session
    @POST("api/auth/google") suspend fun google(@Body body: GoogleRequest): Session
    @POST("api/auth/logout") suspend fun logout(@Body body: RefreshRequest)
    @POST("api/auth/forgot-password") suspend fun forgotPassword(@Body body: ForgotRequest): MessageResponse
    @POST("api/auth/reset-password") suspend fun resetPassword(@Body body: ResetRequest): MessageResponse

    @GET("api/users/me") suspend fun me(): UserMe
    @PUT("api/users/me") suspend fun updateMe(@Body body: UpdateProfile): UserMe
    @PUT("api/users/preferences") suspend fun updatePreferences(@Body body: UpdatePreferences): UserMe

    @GET("api/ingredients/categories") suspend fun categories(): List<Category>
    @GET("api/ingredients") suspend fun ingredients(@Query("q") q: String, @Query("limit") limit: Int = 8): List<CatalogIngredient>

    @GET("api/pantry") suspend fun pantry(@Query("q") q: String? = null, @Query("category") category: String? = null): List<PantryItem>
    @GET("api/pantry/expiring") suspend fun expiring(): ExpiringGroups
    @POST("api/pantry") suspend fun addPantryItem(@Body body: PantryInput): PantryItem
    @POST("api/pantry/bulk") suspend fun addPantryItems(@Body body: PantryBulk): List<PantryItem>
    @PUT("api/pantry/{id}") suspend fun updatePantryItem(@Path("id") id: String, @Body body: JsonObject): PantryItem
    @DELETE("api/pantry/{id}") suspend fun deletePantryItem(@Path("id") id: String)

    @Multipart @POST("api/ai/scan-ingredients") suspend fun scan(@Part image: MultipartBody.Part): ScanResponse
    @POST("api/ai/generate-recipe") suspend fun generateRecipe(@Body body: GenerateRequest): GenerateResponse
    @POST("api/ai/substitute-ingredients") suspend fun substitute(@Body body: SubstituteRequest): SubstituteResponse

    @GET("api/recipes/search")
    suspend fun searchRecipes(
        @Query("q") q: String,
        @Query("minMatch") minMatch: Int = 0,
        @Query("mode") mode: String = "any",
    ): FindResult

    @GET("api/recipes/by-ingredients")
    suspend fun recipesByIngredients(
        @Query("ingredients") ingredients: String? = null,
        @Query("minMatch") minMatch: Int = 0,
        @Query("mode") mode: String = "any",
    ): FindResult

    @GET("api/recipes/recommendations") suspend fun recommendations(): FindResult
    @GET("api/recipes/recently-viewed") suspend fun recentlyViewed(): List<RecipeSummary>
    @GET("api/recipes/{key}") suspend fun recipe(@Path("key") key: String): RecipeDetail

    @GET("api/favorites") suspend fun favorites(@Query("q") q: String? = null, @Query("collectionId") collectionId: String? = null): List<Favorite>
    @POST("api/favorites") suspend fun addFavorite(@Body body: FavoriteRequest): FavoriteCreated
    @PUT("api/favorites/{id}") suspend fun moveFavorite(@Path("id") id: String, @Body body: FavoriteMove): FavoriteCreated
    @DELETE("api/favorites/{id}") suspend fun removeFavorite(@Path("id") id: String)

    @GET("api/collections") suspend fun collections(): List<RecipeCollection>
    @POST("api/collections") suspend fun addCollection(@Body body: CollectionRequest): RecipeCollection
    @DELETE("api/collections/{id}") suspend fun deleteCollection(@Path("id") id: String)

    @GET("api/shopping-lists") suspend fun shoppingLists(): List<ShoppingList>
    @POST("api/shopping-lists") suspend fun addShoppingList(@Body body: ShoppingListRequest): ShoppingList
    @DELETE("api/shopping-lists/{id}") suspend fun deleteShoppingList(@Path("id") id: String)
    @POST("api/shopping-lists/{id}/items") suspend fun addShoppingItems(@Path("id") id: String, @Body body: ShoppingItemsRequest): ShoppingList
    @PUT("api/shopping-lists/{id}/items/{itemId}") suspend fun updateShoppingItem(@Path("id") id: String, @Path("itemId") itemId: String, @Body body: JsonObject): ShoppingItem
    @DELETE("api/shopping-lists/{id}/items/{itemId}") suspend fun deleteShoppingItem(@Path("id") id: String, @Path("itemId") itemId: String)

    @GET("api/meal-plans") suspend fun mealWeek(@Query("weekStart") weekStart: String? = null): MealWeek
    @POST("api/meal-plans") suspend fun planMeal(@Body body: MealPlanRequest): MealPlanItem
    @DELETE("api/meal-plans/{id}") suspend fun removeMeal(@Path("id") id: String)

    @GET("api/history") suspend fun history(): List<ScanHistoryEntry>
    @DELETE("api/history/{id}") suspend fun deleteHistory(@Path("id") id: String)
}
