# SSH Tunnel Exporter — common dev tasks (thin wrappers over ./gradlew)
GRADLEW := ./gradlew

.DEFAULT_GOAL := help
.PHONY: help build test check run verify sign publish release clean tasks install

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

build: ## Build the plugin ZIP -> build/distributions/*.zip
	$(GRADLEW) buildPlugin

install: build ## Build, then install into your local DataGrip (restart DataGrip to load)
	@zip=$$(ls -t build/distributions/*.zip 2>/dev/null | head -1); \
	cfg=$$(ls -dt "$$HOME/Library/Application Support/JetBrains"/DataGrip* 2>/dev/null | head -1); \
	test -n "$$zip" || { echo "✗ no plugin zip in build/distributions/"; exit 1; }; \
	test -n "$$cfg" || { echo "✗ DataGrip config dir not found — run DataGrip once first"; exit 1; }; \
	dst="$$cfg/plugins"; mkdir -p "$$dst"; \
	top=$$(unzip -Z1 "$$zip" | head -1 | cut -d/ -f1); \
	echo "→ installing $$(basename "$$zip") into $$dst"; \
	rm -rf "$$dst/$$top"; \
	unzip -oq "$$zip" -d "$$dst"; \
	echo "✓ installed as '$$top' — restart DataGrip to load the new build."

test: ## Run the unit test suite
	$(GRADLEW) test

check: ## Run all checks (tests + wired verifications)
	$(GRADLEW) check

run: ## Launch DataGrip with the plugin loaded (runIde)
	$(GRADLEW) runIde

verify: ## Run the IntelliJ Plugin Verifier (binary compatibility)
	$(GRADLEW) verifyPlugin

sign: ## Sign the ZIP — needs CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD
	$(GRADLEW) signPlugin

publish: ## Publish to JetBrains Marketplace — needs PUBLISH_TOKEN
	$(GRADLEW) publishPlugin

release: ## Full gate: clean -> test -> verifyPlugin -> buildPlugin
	$(GRADLEW) clean test verifyPlugin buildPlugin

clean: ## Delete the build directory
	$(GRADLEW) clean

tasks: ## List every Gradle task
	$(GRADLEW) tasks --all
