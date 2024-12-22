# Klip - Kotlin Image Processing Server

Klip is a lightweight Kotlin-based image processing server designed to handle dynamic transformations on images stored in AWS S3. It supports resizing, cropping, grayscale filters, and rotation via HTTP GET requests.

---

## Installation + Run

### Prerequisites
- Java 21+ installed

### Run Klip Server

```bash
./gradlew clean build
KLIP_AWS_REGION=us-west-2 \
KLIP_S3_BUCKET=cdn.arrivedhomes.com \
java -jar build/libs/klip-all.jar
```

### Default Endpoint
- Server runs at: `http://0.0.0.0:8080`

---

## API Documentation

### Get Original Image

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

### Resize Image

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

### 3. Grayscale Filter

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

### 4. Center Crop

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

### 5. Rotate Image

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

### 6. Combine Filters

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

## TODO

- Handle crop + resize issues
- Add caching for performance optimization
