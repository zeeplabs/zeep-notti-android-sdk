package com.zeeplabs.notti

import java.text.SimpleDateFormat

// CI red proof (package-foundation T12): lint must fail on a locale-less date format. Never merge.
internal fun ciProofLintYear(): SimpleDateFormat = SimpleDateFormat("yyyy")
