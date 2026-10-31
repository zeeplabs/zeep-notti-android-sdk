package com.zeeplabs.notti

// CI red proof (package-foundation T12): a public member without apiDump must fail apiCheck. Never merge.
public object CiProofAbi {
    public fun probe(): Int = 1
}
