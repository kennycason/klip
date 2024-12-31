# Klip - Kotlin Image Processing Server

Klip is a lightweight Kotlin-based image processing server designed to handle dynamic transformations on images stored in AWS S3.
It supports caching, resizing, cropping, grayscale filters, and rotation via HTTP GET requests.

---

## Build Project

```shell
./gradlew clean build
```

## Environment Configuration (Env)

| Section   | Variable             | Type    | Default                    | Required | Description                                                                   |
|-----------|----------------------|---------|----------------------------|----------|-------------------------------------------------------------------------------|
| **LOG**   | `KLIP_LOG_LEVEL`     | Enum    | `INFO`                     | No       | `TRACE`, `INFO`, `DEBUG`, `WARN`, `ERROR`                                     |
| **HTTP**  | `KLIP_HTTP_PORT`     | Int     | `8080`                     | No       | The HTTP port the server listens on.                                          |
| **AWS**   | `KLIP_AWS_REGION`    | String  | -                          | Yes      | AWS region for S3 bucket (e.g., `us-west-2`).                                 |
| **AWS**   | `KLIP_S3_BUCKET`     | String  | -                          | Yes      | The S3 bucket name where source images are stored.                            |
| **Cache** | `KLIP_CACHE_ENABLED` | Boolean | True                       | No       | If false, disable image cache.                                                |
| **Cache** | `KLIP_CACHE_BUCKET`  | String  | *Same as `KLIP_S3_BUCKET`* | No       | Used if using different S3 bucket for caching.                                |
| **Cache** | `KLIP_CACHE_FOLDER`  | String  | `_cache/`                  | No       | Prefix for cached files. Stored within the cache bucket.                      |
| **Rules** | `KLIP_RULES`         | String  | "" (empty)                 | No       | Inline rule definitions separated by ; (e.g., +flipV;-flipH;dim 32x32 64x64). |
| **Rules** | `KLIP_RULES_FILE`    | String  | -                          | No       | Path to a rules file with one rule per line. Overrides KLIP_RULES.            |

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
GET /img/{path/to/image}?w={width}&h={height}

# Set width and height independently.
GET /img/{path/to/image}?w={width}
GET /img/{path/to/image}?h={height}

# Dimension syntax
GET /img/{path/to/image}?d={width}x{height}
```

Resize the image to the specified width and height.

Query Parameters:

| Parameter | Type | Required | Default | Description                |
|-----------|------|----------|---------|----------------------------|
| `width`   | Int  | Optional | -       | Width of the output image  |
| `height`  | Int  | Optional | -       | Height of the output image |

Example:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?d=640x480
GET http://localhost:8080/img/properties/1/04c08449e126.png?w=640&h480
```

![Resized Image](https://github.com/kennycason/klip/blob/main/images/resized.png?raw=true)

## Fit Modes

Control how the image fits within the specified dimensions.

```bash
GET /img/{path/to/image}?w={width}&h={height}&fit={mode}
```

| Parameter | Type   | Required | Default | Description                                                                    |
|-----------|--------|----------|---------|--------------------------------------------------------------------------------|
| `fit`     | String | Optional | -       | How the image should fit within dimensions. Values: `contain`, `cover`, `fill` |

Fit Modes Explained:

contain: Preserves aspect ratio while ensuring image fits within specified dimensions

```bash
GET /img/photo.jpg?w=800&h=600&fit=contain
```

cover: Fills entire dimensions while preserving aspect ratio, cropping excess

```bash
GET /img/photo.jpg?w=800&h=600&fit=cover
```

fill: Stretches or squishes image to exactly fit dimensions

```bash
GET /img/photo.jpg?w=800&h=600&fit=fill
```

Default Behavior:

- With both width and height: Acts as fill
- With single dimension: Acts as contain

---

## Quality Adjustment

```
GET /img/{path/to/image}?quality=75
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
GET http://localhost:8080/img/properties/1/04c08449e126.png?quality=100
# Medium-High Quality
GET http://localhost:8080/img/properties/102/05013ad4469e00a7aed9596bc37af74e.jpg?quality=75
# Medium Quality
GET http://localhost:8080/img/properties/102/05013ad4469e00a7aed9596bc37af74e.jpg?quality=50
# Low Quality
GET http://localhost:8080/img/properties/102/05013ad4469e00a7aed9596bc37af74e.jpg?quality=25
# Lower Quality
GET http://localhost:8080/img/properties/102/05013ad4469e00a7aed9596bc37af74e.jpg?quality=10
# Combined with resizing
GET http://localhost:8080/img/properties/102/05013ad4469e00a7aed9596bc37af74e.jpg?quality=10&w1301h781
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
GET /img/{path/to/image}?crop?w={width}&h={height}
```

Crop the image from the center to the specified width and height.

Query Parameters:

| Parameter | Type    | Required | Default | Description                      |
|-----------|---------|----------|---------|----------------------------------|
| `crop`    | Boolean | No       | false   | Crops the image to fit the size. |

Example:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?crop&w=250&h=250
```

![Cropped Image](https://github.com/kennycason/klip/blob/main/images/cropped.png?raw=true)

---

## Grayscale Filter

```
GET /img/{path/to/image}?grayscale
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
GET /img/{path/to/image}?flipH
```

Flip the image horizontally (left-to-right).

Query Parameters:

| Parameter | Type    | Required | Default | Description                                   |
|-----------|---------|----------|---------|-----------------------------------------------|
| `flipH`   | Boolean | No       | false   | Flips the image horizontally (left-to-right). |

Example:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?flipH
```

![Flipped Horizontally](https://github.com/kennycason/klip/blob/main/images/resized_fliph.png?raw=true)

---

## Flip Vertically

```
GET /img/{path/to/image}?flipV
```

Flip the image vertically (top-to-bottom).

Query Parameters:

| Parameter | Type    | Required | Default | Description                                 |
|-----------|---------|----------|---------|---------------------------------------------|
| `flipV`   | Boolean | No       | false   | Flips the image vertically (top-to-bottom). |

Example - Flip vertically:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?flipV
```

![Flipped Vertically](https://github.com/kennycason/klip/blob/main/images/resized_flipv.png?raw=true)

Example - Flip both horizontally and vertically:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?flipH&flipV
```

![Flipped Both](https://github.com/kennycason/klip/blob/main/images/resized_fliph_flipv.png?raw=true)

---

## Rotate Image

```
GET /img/{path/to/image}?rotate=45
```

Rotate the image by the specified degrees (clockwise).

Query Parameters:

| Parameter | Type  | Required | Default | Description                               |
|-----------|-------|----------|---------|-------------------------------------------|
| `rotate`  | Float | No       | 0       | Rotates the image by the specified angle. |

Example - Rotate image 45 degrees:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?rotate=45
```

![Rotated Image](https://github.com/kennycason/klip/blob/main/images/resized_rotated45.png?raw=true)

---

## Blur Image

```
GET /img/{path/to/image}?blur={radius}x{sigma}
GET /img/{path/to/image}?blur={blur}
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
GET http://localhost:8080/img/properties/1/04c08449e126.png?blur=0x2
```

Example: Simple, heavy blur

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?blur=7
```

![Blurred Image](https://github.com/kennycason/klip/blob/main/images/blur0x2.png?raw=true)

---

## Sharpen Image

```
GET /img/{path/to/image}?sharpen=2.0
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
GET http://localhost:8080/img/properties/1/04c08449e126.png?sharpen=2.0
```

![Blurred Image](https://github.com/kennycason/klip/blob/main/images/sharpen2.0.png?raw=true)

---

## Color Adjustment

```
GET /img/{path/to/image}?colors=16
```

Reduce the number of colors in the image to simplify or stylize it.

Query Parameters:

| Parameter | Type | Required | Default | Description                                     |
|-----------|------|----------|---------|-------------------------------------------------|
| `colors`  | Int  | No       | `256`   | Reduces the image to the specified color count. |

Example - 10 colors:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?colors=10
```

![Blurred Image](https://github.com/kennycason/klip/blob/main/images/colors10.png?raw=true)

---

## Dithering

```
GET /img/{path/to/image}?dither
```

Enable or disable dithering to improve color approximation when reducing colors.

Query Parameters:

| Parameter | Type    | Required | Default | Description       |
|-----------|---------|----------|---------|-------------------|
| `dither`  | Boolean | No       | false   | Enable dithering. |

Example - 10 colors:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?dither
```

---

## Combine Filters

```
GET /img/{path/to/image}?grayscale&crop&rotate=90
```

Apply multiple transformations in a single request.

Example:

```bash
GET http://localhost:8080/img/properties/1/04c08449e126.png?w=250&h=250&grayscale&crop&rotate=90
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
| `fit {values}`     | Allow specific fit values .                 | `fit cover contain`       |

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

## Generate Placeholder Image

```bash
GET /canvas/{width}x{height}
```

Generate a placeholder image with custom dimensions, background color, and optional text overlay.

Query Parameters:

| Parameter     | Type   | Required | Default | Description                                    |
|---------------|--------|----------|---------|------------------------------------------------|
| `bgColor`     | String | Optional | gray    | Background color (name or hex code)            |
| `textColor`   | String | Optional | white   | Text color (name or hex code)                  |
| `textSize`    | Int    | Optional | 20      | Font size for the text                         |
| `text`        | String | Optional | -       | Text to display on the canvas                  |
| `pattern`     | String | Optional | -       | Pattern type: "check", "grid", "stripe"        |
| `patternSize` | Int    | Optional | 20-40   | Size of pattern elements                       |
| `gradient`    | String | Optional | -       | Gradient spec: "blue,red" or "45,#ff0000,blue" |
| `font`        | String | Optional | Arial   | Font family for text                           |
| `textAlign`   | String | Optional | center  | Text alignment: "center", "north", "southwest" |
| `border`      | Int    | Optional | -       | Border width in pixels                         |
| `borderColor` | String | Optional | -       | Border color (name or hex code)                |
| `radius`      | Int    | Optional | 0       | Corner radius for rounded corners              |
| `grayscale`   | Bool   | Optional | false   | Convert to grayscale                           |
| `quality`     | Int    | Optional | -       | Output image quality (1-100)                   |
| `blur`        | String | Optional | -       | Blur effect: "2.5" or "2.5x1.2"                |
| `sharpen`     | Float  | Optional | -       | Sharpen effect intensity                       |
| `rotate`      | Float  | Optional | -       | Rotation angle in degrees                      |
| `flipH`       | Bool   | Optional | false   | Flip horizontally                              |
| `flipV`       | Bool   | Optional | false   | Flip vertically                                |

Examples:

```bash
# Basic gray placeholder
GET /canvas/640x480

# Blue placeholder with text
GET /canvas/200x100?bgColor=blue&text=Hello

# Custom colors and font size
GET /canvas/400x300?bgColor=%23FF0000&textColor=white&text=Preview&textSize=30

# Using hex colors
GET /canvas/300x200?bgColor=%23336699&text=Loading

# More
GET /canvas/300x200?bg=blue&text=Hello
GET /canvas/300x200?gradient=45,blue,red&text=Gradient
GET /canvas/300x200?pattern=check&patternSize=30
GET /canvas/300x200?pattern=grid&patternSize=50&text=Grid
GET /canvas/300x200?bg=white&border=5&borderColor=black&radius=10
GET /canvas/300x200?text=Hello&font=Helvetica&align=north&grayscale=1
```

```bash
GET /canvas/320x320?text=Hello&bgColor=%23336699&textColor=white&flipH&textSize=40&pattern=check&borderColor=black&patternSize=320&border=10
```

![Canvas](https://github.com/kennycason/klip/blob/main/images/placeholder_320x240_hello.png?raw=true)

```bash
GET /canvas/320x320?text=Hello&textSize=40&pattern=check&borderColor=black&patternSize=320&border=10&bgColor=%23336699&textColor=white
```
![Canvas](https://github.com/kennycason/klip/blob/main/images/placeholder_320x320_bordered_grid.png?raw=true)

## Errors

```bash
GET /img/properties/1/04c08449e1261fedc2eb1a6a99245531.png?w=10&h=9
GET /img/properties/1/04c08449e1261fedc2eb1a6a99245531.png?d=10x9
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

```bash
brew install graphicsmagick
```

---

# Docker

## Build the project

```bash
./gradlew clean build
```

## Create Dockerfile from template (one-time step)

```bash
cp Dockerfile.template Dockerfile
```

Afterward set the `ENV` variables as needed.

## Build Docker image

```bash
docker build --platform linux/amd64 -t klip-prod:latest .
```

## Tag and Deploy image

```bash
docker tag klip-prod:latest <AWS_ACCOUNT_ID>.dkr.ecr.<AWS_REGION>.amazonaws.com/klip-prod:latest
docker push <AWS_ACCOUNT_ID>.dkr.ecr.<AWS_REGION>.amazonaws.com/klip-prod:latest
```

## Force deploy of Klip

```bash
aws ecs update-service \
  --cluster klip-prod-cluster \
  --service klip-prod-service \
  --force-new-deployment
```

## Tail Logs

```bash
aws logs tail /ecs/klip-prod --follow
```

# Terraform

ALB, ECS, Fargate, ECR

Note that you'll likely need to adjust this terraform to fit your project.
This is meant as a starting point.

## Setup

```bash
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
