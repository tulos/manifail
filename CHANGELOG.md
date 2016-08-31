# Change Log

## [Unreleased]

## 0.4.0 - 2016-08-31
### Added
- `*last-result*/*retry-count*/*elapsed-ms*` dynamic variables available inside
  the retry block
- `unwrap` to get the value/cause of the marker
### Changed
- `retry!/abort!` accept an object as an argument in addition to a `Throwable` cause
- `retry!/abort!` markers produce `Throwable` exceptions instead of `ExceptionInfo`
- All special exceptions subclass `Throwable` instead of `RuntimeException`

## 0.3.0 - 2016-08-30
### Added
- `reset!` to reset the execution with a new delays sequence

## 0.2.0 - 2016-08-16
### Changed
- `abort!` and `retry!` can take a `Throwable` cause as their argument

## 0.1.0 - 2016-08-15
### Added
- Initial release: `with-retries`, `forever`, `retries`, `delay`,
  `limit-duration`, `limit-retries`

[Unreleased]: https://github.com/your-name/manifail/compare/0.4.0...HEAD
