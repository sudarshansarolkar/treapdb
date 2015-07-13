# Operations of TreapDB #


|**Operation**|**Usage**|**Parameters**|
|:------------|:--------|:-------------|
|put          |insert a new key-value pair, or replace an old key-value pair|key and value |
|get          |get the value of the indicated key |key           |
|bulkPut      |inset 2 or more key-value pairs together|map of key and value|
|bulkGet      |get the value of 2 or more indicated keys |list of keys  |
|prefix       |get the value of the keys with the indicated prefix string|prefix and number of result|
|bulkPrefix   |get the value of the keys with the indicated prefix strings|list of prefix, number of result, beginning key, sort the key-value pairs in ascending or descending order|
|kmax         |get the k maximum  key-value pairs|k             |
|kmin         |get the k minimum key-value pairs|k             |
|range        |get the key-value pairs whose key is between the indicated keys|beginning key and ending key|
|before       |get the key-value pairs whose key is before the indicated key in alphabetical order|key and number of result|
|after        |get the key-value pairs whose key is after the indicated key in alphabetical order|key and number of result|
|length       |get the number of key-value pairs|none          |
|remove       |delete the indicated key-value pair|key           |
|removePrefix |delete value of the keys with indicated prefix string|prefix        |
|optimize     |optimize the space usage of index file|number of nodes need to be optimized(1024 is recommended value)|