# Klip - Kotlin Image Processing Server

Klip is a lightweight Kotlin-based image processing server designed to handle dynamic transformations on images stored in AWS S3.
It supports caching, resizing, cropping, grayscale filters, and rotation via HTTP GET requests.

---

## Build Project

```shell
./gradlew clean build
```

## Environment Configuration (Env)

| Section   | Variable           | Type    | Default                    | Required | Description                                                                   |
|-----------|--------------------|---------|----------------------------|----------|-------------------------------------------------------------------------------|
| **LOG**   | `KLIP_LOG_LEVEL`   | Enum    | `INFO`                     | No       | `TRACE`, `INFO`, `DEBUG`, `WARN`, `ERROR`                                     |
| **HTTP**  | `KLIP_HTTP_PORT`   | Int     | `8080`                     | No       | The HTTP port the server listens on.                                          |
| **AWS**   | `KLIP_AWS_REGION`  | String  | -                          | Yes      | AWS region for S3 bucket (e.g., `us-west-2`).                                 |
| **AWS**   | `KLIP_S3_BUCKET`   | String  | -                          | Yes      | The S3 bucket name where source images are stored.                            |
| **Cache** | `KLIP_CACHE_ENABLED` | Boolean | True                       | No       | If false, disable image cache.                                                |
| **Cache** | `KLIP_CACHE_BUCKET` | String  | *Same as `KLIP_S3_BUCKET`* | No       | Used if using different S3 bucket for caching.                                |
| **Cache** | `KLIP_CACHE_FOLDER` | String  | `_cache/`                  | No       | Prefix for cached files. Stored within the cache bucket.                      |
| **Rules** | `KLIP_RULES_VALIDATION_MODE` | Enum    | `STRICT`                   | No       | `LENIENT` ignore errors, `STRICT` throw exceptions on errors.                 |
| **Rules** | `KLIP_RULES`       | String  | "" (empty)                 | No       | Inline rule definitions separated by ; (e.g., +flipV;-flipH;dim 32x32 64x64). |
| **Rules** | `KLIP_RULES_FILE`  | String  | -                          | No       | Path to a rules file with one rule per line. Overrides KLIP_RULES.  |

---

## Run Klip Server

```bash
KLIP_LOG_LEVEL=INFO \
KLIP_HTTP_PORT=8080 \
KLIP_AWS_REGION=us-west-2 \
KLIP_S3_BUCKET=cdn.klip.com \
KLIP_CACHE_ENABLED=true \
KLIP_CACHE_BUCKET=cdn.klip.com \
KLIP_CACHE_FOLDER=.cache/ \
KLIP_RULES_VALIDATION_MODE=STRICT \
KLIP_RULES="+flipV;-flipH;dim 32x32 64x64 256x256" \
java -jar build/libs/klip-all.jar
```

Default local endpoint: `http://0.0.0.0:8080`

---

# API Documentation

## Get Original Image

```
GET /img/{path/to/image}
```

Fetch the original image stored in the S3 bucket without any transformations.

Example:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png
```

<img src="https://github.com/kennycason/klip/blob/main/images/original.png?raw=true" width="500px"/>

---

## Resize Image

```
GET /img/{width}x{height}/{path/to/image}
```

Resize the image to the specified width and height.

Query Parameters:

| Parameter | Type | Required | Default | Description                |
|-----------|------|----------|---------|----------------------------|
| `width`   | Int  | Yes      | -       | Width of the output image  |
| `height`  | Int  | Yes      | -       | Height of the output image |

Example:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png
```

![Resized Image](https://github.com/kennycason/klip/blob/main/images/resized.png?raw=true)

---

## Quality Adjustment

```
GET /img/{width}x{height}/{path/to/image}?quality=75
```

Adjust the image quality for compression and size optimization.
Useful for reducing image size without significant loss of visual fidelity.

Query Parameters:

| Parameter | Type | Required | Default | Description                                                                  |
|-----------|------|----------|---------|------------------------------------------------------------------------------|
| `quality` | Int  | No       | 100     | Adjusts image quality (1–100). Lower values reduce size but may lose detail. |

Supported Formats: JPEG, PNG, WebP (other formats may default to lossless encoding).

Examples:

```bash
# High Quality (Default)
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?quality=100
# Medium-High Quality
GET http://localhost:8080/img/1301x781/properties/102/05013ad4469e00a7aed9596bc37af74e.jpg?quality=75
# Medium Quality
GET http://localhost:8080/img/1301x781/properties/102/05013ad4469e00a7aed9596bc37af74e.jpg?quality=50
# Low Quality
GET http://localhost:8080/img/1301x781/properties/102/05013ad4469e00a7aed9596bc37af74e.jpg?quality=25
# Lower Quality
GET http://localhost:8080/img/1301x781/properties/102/05013ad4469e00a7aed9596bc37af74e.jpg?quality=10
```

### Image Comparison (click to enlarge)

| High (100) (default)                                                           | Medium-High (75)                                                             | Medium (50)                                                                  |
|--------------------------------------------------------------------------------|------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| ![100%](https://github.com/kennycason/klip/blob/main/images/q100.jpg?raw=true) | ![75%](https://github.com/kennycason/klip/blob/main/images/q75.jpg?raw=true) | ![75%](https://github.com/kennycason/klip/blob/main/images/q50.jpg?raw=true) |
| 991,091 bytes                                                                  | 205,236 bytes                                                                | 132,963 bytes                                                                |

| Low (25)                                                                     | Lower (10)                                                                   |
|------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| ![25%](https://github.com/kennycason/klip/blob/main/images/q25.jpg?raw=true) | ![25%](https://github.com/kennycason/klip/blob/main/images/q10.jpg?raw=true) |
| 83,156 bytes                                                                 | 43,615 bytes                                                                 |

---

## Center Crop

```
GET /img/{width}x{height}/{path/to/image}?crop
```

Crop the image from the center to the specified width and height.

Query Parameters:

| Parameter | Type    | Required | Default | Description                      |
|-----------|---------|----------|---------|----------------------------------|
| `crop`    | Boolean | No       | false   | Crops the image to fit the size. |

Example:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?crop
```

![Cropped Image](https://github.com/kennycason/klip/blob/main/images/cropped.png?raw=true)

---

## Grayscale Filter

```
GET /img/{width}x{height}/{path/to/image}?grayscale
```

Convert the image to grayscale while resizing to the specified dimensions.

Query Parameters:

| Parameter   | Type    | Required | Default | Description                              |
|-------------|---------|----------|---------|------------------------------------------|
| `grayscale` | Boolean | No       | false   | Applies a grayscale filter to the image. |

Example:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?grayscale
```

![Grayscale Image](https://github.com/kennycason/klip/blob/main/images/resized_grayscale.png?raw=true)

---

## Flip Horizontally

```
GET /img/{width}x{height}/{path/to/image}?flipH
```

Flip the image horizontally (left-to-right).

Query Parameters:

| Parameter | Type    | Required | Default | Description                                   |
|-----------|---------|----------|---------|-----------------------------------------------|
| `flipH`   | Boolean | No       | false   | Flips the image horizontally (left-to-right). |

Example:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?flipH
```

![Flipped Horizontally](https://github.com/kennycason/klip/blob/main/images/resized_fliph.png?raw=true)

---

## Flip Vertically

```
GET /img/{width}x{height}/{path/to/image}?flipV
```

Flip the image vertically (top-to-bottom).

Query Parameters:

| Parameter | Type    | Required | Default | Description                                 |
|-----------|---------|----------|---------|---------------------------------------------|
| `flipV`   | Boolean | No       | false   | Flips the image vertically (top-to-bottom). |

Example - Flip vertically:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?flipV
```

![Flipped Vertically](https://github.com/kennycason/klip/blob/main/images/resized_flipv.png?raw=true)

Example - Flip both horizontally and vertically:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?flipH&flipV
```

![Flipped Both](https://github.com/kennycason/klip/blob/main/images/resized_fliph_flipv.png?raw=true)

---

## Rotate Image

```
GET /img/{width}x{height}/{path/to/image}?rotate=45
```

Rotate the image by the specified degrees (clockwise).

Query Parameters:

| Parameter | Type  | Required | Default | Description                               |
|-----------|-------|----------|---------|-------------------------------------------|
| `rotate`  | Float | No       | 0       | Rotates the image by the specified angle. |

Example - Rotate image 45 degrees:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?rotate=45
```

![Rotated Image](https://github.com/kennycason/klip/blob/main/images/resized_rotated45.png?raw=true)

---

## Blur Image

```
GET /img/{blurRadius}x{blurSigma}/{path/to/image}?blur=0x2
```

Apply a **Gaussian blur** to the image to soften details.

Query Parameters:

| Parameter | Type   | Required | Default | Description                                          |
|-----------|--------|----------|---------|------------------------------------------------------|
| `blur`    | Int    | No       | 0       | Simple blur with a single integer, range: `[1, 10]`. |
| `blur`    | String | No       | 0       | Fine-tune Gaussian blur, format: `{radius}x{sigma}`. |

`blur` format mappings:

| Blur | Radius | Sigma | Description                       |
|------|--------|-------|-----------------------------------|
| 1    | 1      | 0.5   | Very light blur                   |
| 2    | 2      | 1.0   | Light blur                        |  
| 3    | 3      | 1.5   | Moderate blur                     |
| 4    | 4      | 2.0   | Strong blur                       |
| 5    | 5      | 2.5   | Very strong blur                  |
| 10   | 10     | 5.0   | Extreme blur (background effects) |

- **`radius`**: Defines the area of the blur effect (higher = wider blur).
- **`sigma`**: Controls the strength of the blur (higher = softer edges).

Example - Mild blur:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?blur=0x2
```

Example: Simple, heavy blur

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?blur=7
```

![Blurred Image](https://github.com/kennycason/klip/blob/main/images/blur0x2.png?raw=true)

---

## Sharpen Image

```
GET /img/{width}x{height}/{path/to/image}?sharpen=2.0
```

Sharpen the image to enhance details and make edges clearer.

Query Parameters:

| Parameter | Type  | Required | Default | Description                                      |
|-----------|-------|----------|---------|--------------------------------------------------|
| `sharpen` | Float | No       | `0`     | Applies sharpening in the format `radiusxsigma`. |

- **`radius`**: Defines the area of the blur effect (higher = wider blur).
- **`sigma`**: Controls the strength of the blur (higher = softer edges).

Example - Mild blur:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?sharpen=2.0
```

![Blurred Image](https://github.com/kennycason/klip/blob/main/images/sharpen2.0.png?raw=true)

---

## Color Adjustment

```
GET /img/{width}x{height}/{path/to/image}?colors=16
```

Reduce the number of colors in the image to simplify or stylize it.

Query Parameters:

| Parameter | Type | Required | Default | Description                                     |
|-----------|------|----------|---------|-------------------------------------------------|
| `colors`  | Int  | No       | `256`   | Reduces the image to the specified color count. |

Example - 10 colors:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?colors=10
```

![Blurred Image](https://github.com/kennycason/klip/blob/main/images/colors10.png?raw=true)

---

## Dithering

```
GET /img/{width}x{height}/{path/to/image}?dither
```

Enable or disable dithering to improve color approximation when reducing colors.

Query Parameters:

| Parameter | Type    | Required | Default | Description       |
|-----------|---------|----------|---------|-------------------|
| `dither`  | Boolean | No       | false   | Enable dithering. |

Example - 10 colors:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?dither
```

---

## Combine Filters

```
GET /img/{width}x{height}/{path/to/image}?grayscale&crop&rotate=90
```

Apply multiple transformations in a single request.

Example:

```bash
GET http://localhost:8080/img/250x250/properties/1/04c08449e126.png?grayscale&crop&rotate=90
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

## Status Check (Admin only: Coming Soon)

```
GET /admin/status
```

Get detailed status information about Klip

Response:

```
{
    "totalRequests": 4182,
    "cacheHits": 3891,
    "cacheHitRate": 0.93041607
}
```

---

## Klip Rules Configuration

Klip allows you to configure transformation rules for image processing using either **environment variables** or a **rules file**. These rules enforce
constraints on transformations, ensuring only allowed operations are applied.

---

### Environment Variable Configuration

Set rules using the `KLIP_RULES` environment variable as a **semicolon-separated string**:

Example:

```bash
KLIP_RULES="+flipV;-flipH;dim 32x32 64x64 128x128;blur 1 2 3 4;quality 75 85 90;rotate 0 90 180"
```

---

### File-Based Configuration

Alternatively, rules can be stored in a **rules.txt** file and referenced via the `KLIP_RULES_FILE` environment variable.

Example:

```bash
KLIP_RULES_FILE=/app/config/rules.txt
```

### rules.txt

```
+flipV
-flipH
dim 32x32 64x64 128x128
blur 1 2 3 4
quality 75 85 90
rotate 0 90 180
```

---

### Rule Syntax

| Rule               | Description                                 | Example                   |
|--------------------|---------------------------------------------|---------------------------|
| `+flipV`           | Allow vertical flipping.                    | `+flipV`                  |
| `-flipV`           | Disallow vertical flipping.                 | `-flipV`                  |
| `+flipH`           | Allow horizontal flipping.                  | `+flipH`                  |
| `-flipH`           | Disallow horizontal flipping.               | `-flipH`                  |
| `+grayscale`       | Allow grayscale conversion.                 | `+grayscale`              |
| `-grayscale`       | Disallow grayscale conversion.              | `-grayscale`              |
| `dim {WxH}`        | Allow specific dimensions (width x height). | `dim 32x32 64x64 128x128` |
| `blur {values}`    | Allow specific blur radii.                  | `blur 1 2 3 4`            |
| `quality {values}` | Allow specific quality settings.            | `quality 75 85 90`        |
| `rotate {values}`  | Allow specific rotation angles in degrees.  | `rotate 0 90 180`         |
| `sharpen {values}` | Allow specific sharpen values.              | `sharpen 0.5 1.0 2.0`     |
| `colors {values}`  | Allow specific color palette sizes.         | `colors 16 32 64 128`     |

---

### Behavior Modes

Klip supports **two validation modes** for rules:

- **STRICT** - Throws an error when a rule is violated (default).
- **LENIENT** - Resets invalid values to defaults (e.g., nullifies transformations).

---

### Usage in Docker

Environment Variable Example:

```bash
docker run -e KLIP_RULES="+flipV;-flipH;dim 32x32 64x64 128x128" klip-app
```

File Example:

```bash
docker run -e KLIP_RULES_FILE=/config/rules.txt -v /local/config:/config klip-app
```

---

## **6. Testing Rules Locally**

To test rules without deployment:

1. Create a **rules.txt** file:
   ```
   +flipV
   -flipH
   dim 32x32 64x64 128x128
   blur 1 2 3 4
   quality 75 85 90
   rotate 0 90 180
   ```

Use the following shell script:

```bash
KLIP_RULES_FILE=./klip_rules.txt ./gradlew run
```

Alternatively, set directly as an environment variable:

```bash
KLIP_RULES="+flipV;-flipH;dim 32x32 64x64 128x128" ./gradlew run
```

---

## Errors

```shell
GET /img/10x9/properties/1/04c08449e1261fedc2eb1a6a99245531.png
```

422 - Unprocessable Entity

```json
{
  "error": "Dimensions must be > 10. Got: 10x9"
}
```

## Installation

### Prerequisites

- Java 21+
- Gradle
- GraphicsMagick

```shell
brew install graphicsmagick
```

---

# Docker

## Build the project

```shell
./gradlew clean build
```

## Create Dockerfile from template (one-time step)

```shell
cp Dockerfile.template Dockerfile
```

Afterward set the `ENV` variables as needed.

## Build Docker image

```shell
docker build --platform linux/amd64 -t klip-prod:latest .
```

## Tag and Deploy image

```shell
docker tag klip-prod:latest <AWS_ACCOUNT_ID>.dkr.ecr.<AWS_REGION>.amazonaws.com/klip-prod:latest
docker push <AWS_ACCOUNT_ID>.dkr.ecr.<AWS_REGION>.amazonaws.com/klip-prod:latest
```

## Force deploy of Klip

```shell
aws ecs update-service \
  --cluster klip-prod-cluster \
  --service klip-prod-service \
  --force-new-deployment
```

## Tail Logs

```shell
aws logs tail /ecs/klip-prod --follow
```

# Terraform

ALB, ECS, Fargate, ECR

Note that you'll likely need to adjust this terraform to fit your project.
This is meant as a starting point.

## Setup

```shell
cd terraform/stacks/
cp prod-template prod
```

Make whatever edits you need to the terraform/variables.

```shell
cd terraform/stacks/prod/
terraform init
terraform apply
```

# Roadmap

- Whitelist filters to prevent abuse
- Configurable Secret Key to protect admin endpoints (clear cache, get stats)
- Migrate to Kotlin Native after (aws s3 client is for kotlin jvm)
- Configurable backend storage (S3 vs File)
