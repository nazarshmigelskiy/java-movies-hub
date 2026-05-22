package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoviesApiTest {
    private static final String BASE = "http://localhost:8080"; // !!! добавьте базовую часть URL
    private static MoviesServer server;
    private static HttpClient client;
    private static final MoviesStore store = new MoviesStore();
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    private final Gson gson = new Gson();

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @BeforeEach
    void beforeEach() {
        store.cleanStore();
    }

    @Test
    void getMoviesWhenEmptyReturnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMoviesShouldReturnAddedMovies() throws IOException, InterruptedException {
        store.addMovie(new Movie("Интерстеллар", 2014, 1));
        store.addMovie(new Movie("Начало", 2010, 2));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken());

        assertEquals(2, movies.size());
        assertEquals("Интерстеллар", movies.getFirst().getName());
        assertEquals(2014, movies.get(0).getYear());
        assertEquals(1, movies.get(0).getId());
        assertEquals("Начало", movies.get(1).getName());
        assertEquals(2010, movies.get(1).getYear());
        assertEquals(2, movies.get(1).getId());
        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
    }

    private HttpResponse<String> postMovie(String body, String contentType)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void postMovieShouldAddMovieWithCorrectData() throws IOException, InterruptedException {
        HttpResponse<String> response = postMovie(
                "{\n" +
                "  \"title\": \"Начало\",\n" +
                "  \"year\": 2010\n" +
                "}\n", CT_JSON);
        assertEquals(201, response.statusCode());
        Movie movie = gson.fromJson(response.body(), Movie.class);
        assertEquals("Начало", movie.getName());
        assertEquals(2010, movie.getYear());
        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
    }

    @Test
    void postMovieShouldReturnErrorWhenTitleIsEmpty() throws IOException, InterruptedException {
        HttpResponse<String> response = postMovie(
                "{\n" +
                "  \"title\": \"\",\n" +
                "  \"year\": 2010\n" +
                "}\n", CT_JSON);
        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("Ошибка валидации"));
        assertTrue(response.body().contains("название не должно быть пустым"));
    }

    @Test
    void postMovieShouldReturnErrorWhenTitleIsTooLong() throws IOException, InterruptedException {
        String s = "a".repeat(101);
        HttpResponse<String> response = postMovie(
                ("{\n" +
                 "  \"title\": \"%s\",\n" +
                 "  \"year\": 2010\n" +
                 "}\n").formatted(s), CT_JSON);
        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("Ошибка валидации"));
    }

    @Test
    void postMovieShouldReturnErrorWhenYearIsLessThan1888() throws IOException, InterruptedException {
        HttpResponse<String> response = postMovie(
                "{\n" +
                "  \"title\": \"Начало\",\n" +
                "  \"year\": 1800\n" +
                "}\n", CT_JSON);
        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("Ошибка валидации"));
    }

    @Test
    void postMovieShouldReturnErrorWhenYearIsGreaterThanNextYear() throws IOException, InterruptedException {
        int invalidYear = LocalDate.now().getYear() + 2;
        HttpResponse<String> response = postMovie(
                ("{\n" +
                 "  \"title\": \"Начало\",\n" +
                 "  \"year\": %d\n" +
                 "}\n").formatted(invalidYear), CT_JSON);
        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("Ошибка валидации"));
    }

    @Test
    void postMovieShouldReturnErrorWhenContentTypeIsWrong() throws IOException, InterruptedException {
        HttpResponse<String> response = postMovie(
                "{\n" +
                "  \"title\": \"Начало\",\n" +
                "  \"year\": 2010\n" +
                "}\n", "text/plain");
        assertEquals(415, response.statusCode());
    }

    @Test
    void postMovieShouldReturnErrorWhenJsonIsInvalid() throws IOException, InterruptedException {
        HttpResponse<String> response = postMovie(
                "{\n" +
                "  \"title\": \"Начало\",\n" +
                "  \"year\":\n" +
                "}\n", CT_JSON);
        assertEquals(400, response.statusCode());
    }

    private HttpResponse<String> getMovieById(int id) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void getMovieByIdShouldReturnMovieWhenMovieExists() throws IOException, InterruptedException {
        store.addMovie(new Movie("Начало", 2010, 1));
        Movie addedMovie = store.getMovies().getFirst();
        HttpResponse<String> response = getMovieById(addedMovie.getId());
        assertEquals(200, response.statusCode());
        Movie movie = gson.fromJson(response.body(), Movie.class);
        assertEquals(addedMovie.getId(), movie.getId());
        assertEquals("Начало", movie.getName());
        assertEquals(2010, movie.getYear());
        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
    }

    @Test
    void getMovieByIdShouldReturnErrorWhenMovieNotFound() throws IOException, InterruptedException {
        HttpResponse<String> response = getMovieById(999);
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Фильм не найден"));
    }

    @Test
    void getMovieByIdShouldReturnErrorWhenIdIsNotNumber() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Некорректный ID"));
    }

    @Test
    void deleteMovieShouldDeleteMovieWhenMovieExists() throws IOException, InterruptedException {
        store.addMovie(new Movie("Начало", 2010, 1));
        Movie movie = store.getMovies().getFirst();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movie.getId()))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());
        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
        HttpResponse<String> getResponse = getMovieById(movie.getId());
        assertEquals(404, getResponse.statusCode());
    }

    @Test
    void deleteMovieShouldReturnErrorWhenMovieNotFound() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Фильм не найден"));

    }

    @Test
    void deleteMovieShouldReturnErrorWhenIdIsNotNumber() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/qwe"))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Некорректный ID"));
    }

    @Test
    void getMoviesByYearShouldReturnMoviesOfSpecifiedYear() throws IOException, InterruptedException {
        store.addMovie(new Movie("Начало", 2010, 1));
        store.addMovie(new Movie("Интерстеллар", 2014, 2));
        store.addMovie(new Movie("Остров проклятых", 2010, 3));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2010"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken());
        assertEquals(2, movies.size());
        assertTrue(movies.stream()
                .allMatch(movie -> movie.getYear() == 2010));
    }

    @Test
    void getMoviesByYearShouldReturnEmptyListWhenMoviesNotFound() throws IOException, InterruptedException {
        store.addMovie(new Movie("Начало", 2010, 1));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=1999"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String contentTypeHeaderValue =
                response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMoviesByYearShouldReturnErrorWhenYearIsNotNumber() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=qwe"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Некорректный параметр запроса"));
    }

    @Test
    void notAllowedMethodShouldReturnError() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }
}