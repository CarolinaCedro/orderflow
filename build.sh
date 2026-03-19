#!/usr/bin/env bash
# =============================================================================
# OrderFlow — Build Script (PRO VERSION)
# =============================================================================

set -euo pipefail

# ─── Colors ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ─── Project Root ─────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# ─── Flags ────────────────────────────────────────────────────────────────────
START_DOCKER=false
FULL_MODE=false
CLEAN_BUILD=false
RUN_TESTS=true

for arg in "$@"; do
    case $arg in
        --docker) START_DOCKER=true ;;
        --full)   START_DOCKER=true; FULL_MODE=true ;;
        --clean)  CLEAN_BUILD=true ;;
        --skip-tests) RUN_TESTS=false ;;
        --help)
            echo "Usage: ./build.sh [--docker] [--full] [--clean] [--skip-tests]"
            exit 0
            ;;
    esac
done

# ─── Helpers ──────────────────────────────────────────────────────────────────
log_info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()    { echo -e "${BLUE}${BOLD}[STEP]${NC}  $*"; }
log_section() { echo -e "\n${CYAN}${BOLD}═══ $* ═══${NC}\n"; }

fail() {
    log_error "$*"
    exit 1
}

# ─── Step 1: Validate Java ────────────────────────────────────────────────────
log_section "Validating Prerequisites"

log_step "Checking Java..."

if ! command -v java &>/dev/null; then
    fail "Java not found. Install Java 17."
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
[ "$JAVA_VERSION" != "17" ] && fail "Java 17 required. Found $JAVA_VERSION"

log_info "Java 17 OK"

# ─── Step 2: Maven ────────────────────────────────────────────────────────────
log_step "Checking Maven..."

if [ -f "$PROJECT_ROOT/mvnw" ]; then
    MVN="$PROJECT_ROOT/mvnw"
else
    command -v mvn >/dev/null || fail "Maven not installed"
    MVN="mvn"
fi

# ─── Step 3: POM ──────────────────────────────────────────────────────────────
[ ! -f "$PROJECT_ROOT/pom.xml" ] && fail "pom.xml not found"

# ─── Step 4: Docker ───────────────────────────────────────────────────────────
if [ "$START_DOCKER" = true ]; then
    log_section "Starting Docker"

    MONGO_COMPOSE="$PROJECT_ROOT/@docker-compose/mongo-db/docker-compose.yml"
    KAFKA_COMPOSE="$PROJECT_ROOT/@docker-compose/kafka/docker-compose.yml"

    command -v docker >/dev/null || log_warn "Docker not installed"

    [ -f "$MONGO_COMPOSE" ] && docker compose -f "$MONGO_COMPOSE" up -d
    [ -f "$KAFKA_COMPOSE" ] && docker compose -f "$KAFKA_COMPOSE" up -d

    log_step "Waiting for services..."

    # Mongo Healthcheck
    until nc -z localhost 27018; do
        log_warn "Waiting Mongo..."
        sleep 2
    done
    log_info "Mongo ready"

    # Kafka Healthcheck
    until nc -z localhost 9091; do
        log_warn "Waiting Kafka..."
        sleep 2
    done
    log_info "Kafka ready"
fi

# ─── Step 5: Build ─────────────────────────────────────────────────────────────
log_section "Building Project"

cd "$PROJECT_ROOT"

MVN_GOAL="install"
[ "$CLEAN_BUILD" = true ] && MVN_GOAL="clean install"

MVN_ARGS="-T 1C"
[ "$RUN_TESTS" = false ] && MVN_ARGS="$MVN_ARGS -DskipTests"

log_step "$MVN $MVN_GOAL $MVN_ARGS"

BUILD_START=$(date +%s)

if $MVN $MVN_GOAL $MVN_ARGS | tee build.log; then
    BUILD_TIME=$(( $(date +%s) - BUILD_START ))
    log_info "Build OK in ${BUILD_TIME}s"
else
    log_error "Build FAILED"
    log_error "Last logs:"
    tail -n 20 build.log
    exit 1
fi

# ─── Step 6: Validate JARs ────────────────────────────────────────────────────
log_section "Validating Artifacts"

MODULES=(
    order-model
    order-rest-service
    order-utils
    order-security-server
    gateway-server
    eureka-server
    config-server
    order-service
    payment-service
    notification-service
    inventory-service
)

for module in "${MODULES[@]}"; do
    if ls "$PROJECT_ROOT/$module/target/"*.jar &>/dev/null; then
        log_info "✓ $module"
    else
        log_warn "✗ $module"
    fi
done

# ─── Step 7: Guide ────────────────────────────────────────────────────────────
if [ "$FULL_MODE" = true ]; then
    log_section "Startup Guide"

    echo "Start order:"
    echo "1. Eureka → 8761"
    echo "2. Security → 9999"
    echo "3. Services"
    echo "4. Gateway → 8080"

    echo ""
    echo "Test:"
    echo "curl -X POST http://localhost:8080/order-security-server/auth/login"
fi

log_section "DONE"
log_info "Use --full for startup guide"