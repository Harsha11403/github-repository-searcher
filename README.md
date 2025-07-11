# GitHub Repository Searcher

This is a Spring Boot WebFlux application designed to search for GitHub repositories using the GitHub API, store the results in a database, and allow retrieval of these stored repositories with various filtering and sorting options.

---

## Table of Contents
- [Features](#features)
- [Technologies Used](#technologies-used)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
  - [Clone the Repository](#clone-the-repository)
  - [Build the Project](#build-the-project)
  - [Run the Application](#run-the-application)
- [API Endpoints](#api-endpoints)
  - [Search Repositories (POST)](#search-repositories-post)
  - [Get Stored Repositories (GET)](#get-stored-repositories-get)
- [Database Configuration](#database-configuration)
- [Error Handling](#error-handling)
- [Running Tests](#running-tests)
- [Future Enhancements](#future-enhancements)

---

## Features
- **GitHub API Integration**: Fetches repository data from the public GitHub API.
- **Reactive Programming**: Built with Spring WebFlux and Project Reactor for non-blocking I/O operations.
- **Data Persistence**: Stores fetched data using Spring Data JPA (H2 DB by default).
- **Intelligent Saving**: Prevents duplicates and updates existing records.
- **Custom Exception Handling**: Handles API rate limits, client errors, and validation failures.
- **Filtering & Sorting**: Retrieve repositories based on language, stars, and sorting options.
- **Comprehensive Testing**: Unit and integration tests included.

---

## Technologies Used
- Java 17+
- Spring Boot 3.x (WebFlux, Data JPA)
- Project Reactor
- Maven
- H2 Database (in-memory)
- Lombok
- Jackson
- JUnit 5
- Mockito
- WebClient (Spring's reactive HTTP client)

---

## Prerequisites
Ensure the following are installed:
- Java 17 or higher
- Maven 3.6+
- Git

---

## Getting Started

### Clone the Repository
```bash
git clone https://github.com/YOUR_USERNAME/github-repository-searcher.git
cd github-repository-searcher
```
> Replace `YOUR_USERNAME` with your actual GitHub username.

### Build the Project
```bash
mvn clean install
```

### Run the Application
```bash
mvn spring-boot:run
```
Application will run on: `http://localhost:8080`

---

## API Endpoints

### Search Repositories (POST)
- **URL:** `/api/github/search`
- **Method:** `POST`
- **Content-Type:** `application/json`

#### Request Body Example
```json
{
  "query": "spring boot",
  "language": "Java",
  "sort": "stars"
}
```

#### Success Response (200 OK)
```json
{
  "message": "Repositories fetched and saved successfully",
  "repositories": [
    {
      "id": 12345,
      "name": "spring-boot",
      "description": "Spring Boot project",
      "owner": "spring-projects",
      "language": "Java",
      "starsCount": 100000,
      "forksCount": 50000,
      "lastUpdated": "2024-01-01T12:00:00Z"
    }
  ]
}
```

#### Error Responses
- `400 Bad Request`
- `422 Unprocessable Entity`
- `429 Too Many Requests`
- `500 Internal Server Error`

---

### Get Stored Repositories (GET)
- **URL:** `/api/github/repositories`
- **Method:** `GET`
- **Query Params:** `language`, `minStars`, `sort`

#### Example Request
```
http://localhost:8080/api/github/repositories?language=Java&minStars=1000&sort=forks
```

#### Success Response (200 OK)
```json
[
  {
    "id": 12345,
    "name": "spring-boot",
    "description": "Spring Boot project",
    "owner": "spring-projects",
    "language": "Java",
    "starsCount": 100000,
    "forksCount": 50000,
    "lastUpdated": "2024-01-01T12:00:00Z"
  }
]
```

---

## Database Configuration
- **Default DB:** H2 (in-memory)
- **H2 Console:** `http://localhost:8080/h2-console`
- **JDBC URL:** `jdbc:h2:mem:testdb`

To use another DB, edit `src/main/resources/application.properties`.

---

## Error Handling
Handled by `GlobalExceptionHandler` for:
- Validation errors (400)
- GitHub API rate limit (429)
- GitHub client/server errors (4xx/5xx)
- Generic server errors (500)

---

## Running Tests
```bash
mvn test
```

---

## Future Enhancements
- Authentication/Authorization
- Pagination
- Granular Error Handling
- Caching
- Asynchronous Processing

---

