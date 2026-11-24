({ meta: { earliestClose: $earliest_close, snapshot: $snapshot, generated: $generated }, rows: . }
  | tojson
  | split("<")
  | join("\\u003c")) as $json
| $tpl
| split("__REPORT_DATA__")
| join($json)
