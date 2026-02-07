# sqles

A Clojure library designed to convert SQL statements into Elasticsearch requests

[![CircleCI](https://circleci.com/gh/punit-naik/sqles/tree/master.svg?style=svg)](https://circleci.com/gh/punit-naik/sqles/tree/master)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.punit-naik/sqles.svg)](https://clojars.org/org.clojars.punit-naik/sqles)

## Installation

Add to `project.clj`:

```clj
[org.clojars.punit-naik/sqles "0.1.0"]
```

## Quickstart

```clj
(require '[org.clojars.punit-naik.sqles.parse-sql :as sql])

(sql/parse-query "select * from my-index")
;; => {:url "http://localhost:9200/my-index/_search"
;;     :body {:query {:match_all {}}}
;;     :method :post}
```

```clj
(sql/parse-query "select name, age from users where age >= 21 order by age desc limit 10")
```

## Configuration

The Elasticsearch server settings are read from:

- Java properties
- A config file set via `:conf`
- Environment variables

Supported settings:

- `ES_PROTOCOL` (default: `http`)
- `ES_HOSTNAME` (required)
- `ES_PORT` (required)
- `ES_USERNAME` (optional)
- `ES_PASSWORD` (optional)

If the server is not reachable, `parse-query` will throw.

## Supported SQL

- `SELECT` fields or `*`
- `FROM`
- `WHERE` with operators: `=`, `!=`, `<>`, `<`, `<=`, `>`, `>=`, `IN`, `BETWEEN`
- `LIMIT`
- `ORDER BY` with `asc` or `desc`
- `COUNT(*)`

## Limitations

- No joins, group by, or aggregations
- `WHERE` precedence does not follow SQL operator precedence
- Nested parentheses support is limited
- Only basic scalar literals are supported

## Development

### Tests

```bash
lein test
```

## CLI

You can run the parser and query runner via `-main`:

```bash
lein run "select * from my-index limit 5"
```

## Docs

[API Docs](https://punit-naik.github.io/sqles)

### Code Coverage

[Code Coverage Report](https://punit-naik.github.io/sqles/coverage)

## License

Copyright © 2021 [Punit Naik](https://github.com/punit-naik)

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
