# Klip - Kotlin Image Processing Server

Klip is a lightweight Kotlin-based image processing server designed to handle dynamic transformations on images stored in AWS S3. 
It supports caching, resizing, cropping, grayscale filters, and rotation via HTTP GET requests.

---

# Installation + Run

## Prerequisites
- Java 21+ installed


##  Environment Configuration (Env)

| Section | Variable                | Type   | Default       | Description                                                             |
|---------|-------------------------|--------|---------------|-------------------------------------------------------------------------|
| **HTTP**| `KLIP_HTTP_PORT`        | Int    | `8080`        | The HTTP port the server listens on.                                    |
| **AWS** | `KLIP_AWS_REGION`       | String | -             | AWS region for S3 bucket (e.g., `us-west-2`).                           |
| **AWS** | `KLIP_S3_BUCKET`        | String | -             | The S3 bucket name where source images are stored.                      |
| **Cache**| `KLIP_CACHE_BUCKET`    | String | *Same as `KLIP_S3_BUCKET`*  | Optional. S3 bucket name for caching transformed images.                |
| **Cache**| `KLIP_CACHE_FOLDER`    | String | `_cache/`     | Prefix for cached files. Stored within the cache bucket.                |

---

## Run Klip Server

```bash
KLIP_HTTP_PORT=8080 \
KLIP_AWS_REGION=us-west-2 \
KLIP_S3_BUCKET=cdn.klip.com \
KLIP_CACHE_BUCKET=cdn.klip.com \
KLIP_CACHE_FOLDER=.cache/ \
java -jar build/libs/klip-all.jar
```

Default local endpoint

- `http://0.0.0.0:8080`

---

# API Documentation

## Get Original Image

```
GET /img/{path/to/image}
```

Fetch the original image stored in the S3 bucket without any transformations.

Example:

```bash
GET http://localhost:8080/img/properties/1/04c08449e1261fedc2eb1a6a99245531.png
```

<img src="https://github.com/kennycason/klip/blob/main/images/original.png?raw=true" width="500px"/>

---

## Resize Image

```
GET /img/{width}x{height}/{path/to/image}
```

Resize the image to the specified width and height.

Query Parameters:  

| Parameter | Type   | Required | Default | Description                   |
|-----------|--------|----------|---------|-------------------------------|
| `width`   | Int    | Yes      | -       | Width of the output image     |
| `height`  | Int    | Yes      | -       | Height of the output image    |

Example:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png
```
  
![Resized Image](https://github.com/kennycason/klip/blob/main/images/resized.png?raw=true)

---

## Grayscale Filter

```
GET /img/{width}x{height}/{path/to/image}?grayscale=true
```

Convert the image to grayscale while resizing to the specified dimensions.

Query Parameters:  

| Parameter   | Type    | Required | Default | Description                                  |
|-------------|---------|----------|---------|----------------------------------------------|
| `grayscale` | Boolean | No       | false   | Applies a grayscale filter to the image.     |

Example:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?grayscale=true
```

![Grayscale Image](https://github.com/kennycason/klip/blob/main/images/resized_and_grayscale.png?raw=true)

---

## Center Crop

```
GET /img/{width}x{height}/{path/to/image}?crop=true
```

Crop the image from the center to the specified width and height.

Query Parameters:  

| Parameter | Type    | Required | Default | Description                      |
|-----------|---------|----------|---------|----------------------------------|
| `crop`    | Boolean | No       | false   | Crops the image to fit the size. |

Example:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?crop=true
```
 
![Cropped Image](https://github.com/kennycason/klip/blob/main/images/cropped.png?raw=true)

---

## Rotate Image

```
GET /img/{width}x{height}/{path/to/image}?rotate=45
```

Rotate the image by the specified degrees (clockwise).

Query Parameters:  

| Parameter | Type   | Required | Default | Description                                    |
|-----------|--------|----------|---------|------------------------------------------------|
| `rotate`  | Float  | No       | 0       | Rotates the image by the specified angle.      |

Example - Rotate image 45 degrees:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?rotate=45
```

![Rotated Image](https://github.com/kennycason/klip/blob/main/images/resized_rotated45.png?raw=true)

---

## Combine Filters

```
GET /img/{width}x{height}/{path/to/image}?grayscale=true&crop=true&rotate=90
```
 
Apply multiple transformations in a single request.

Example:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e1261fedc2eb1a6a99245531.png?grayscale=true&crop=true&rotate=90
```
  
![Combined Filters](https://github.com/kennycason/klip/blob/main/images/combined_transforms.png?raw=true)

---

## Health Check

```
GET /health
```

Check if the server is up and running.

Response:

```json
{ 
     "status": "UP"
}
```

---

## Version Check

```
GET /version
```
 
Get the current version of the Klip server.

Response:

```
1.0.0
```

---
