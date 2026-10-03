SBT := sbt

.PHONY: \
	install \
	build \
	run-server \
	run-client \
	test \
	clean \
	fmt \
	fmt-check \
	lint \
	lint-fix \
	check

install:
	@$(SBT) update

build:
	@$(SBT) compile

run-server:
	@$(SBT) "server/run $(ARGS)"

run-client:
	@$(SBT) "client/run $(ARGS)"

test:
	@$(SBT) test

clean:
	@$(SBT) clean

fmt:
	@$(SBT) scalafmtAll scalafmtSbt

fmt-check:
	@$(SBT) scalafmtCheckAll scalafmtSbtCheck

lint:
	@$(SBT) "scalafixAll --check"

lint-fix:
	@$(SBT) scalafixAll

check: fmt-check lint test