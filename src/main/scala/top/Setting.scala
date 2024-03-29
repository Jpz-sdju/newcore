package top

trait Setting {
  // General Parameter for NutShell
  val XLEN =  64
  val HasCExtension = false
  val HasDiv = true
  val HasIcache = true
  val HasDcache = true
  val AddrBits = 64 // AddrBits is used in some cases
  val VAddrBits = 39
  val PAddrBits = 32 // PAddrBits is Phyical Memory addr bits
  val DataBits = XLEN
  val DataBytes = DataBits / 8
  val FetchBytes = 8

  val ways = 4
  val banks = 8


  val LineSize = 64
  val ResetVector = 0x80000000L
  val MMIOBase = 0x40000000L
  val MMIOSize = 0x40000000L
  val UnCacheBase = 0x10000000L
  val UnCacheSize = 0x50000000L
  val CLINTBase = 0x38000000L          // for bin compiled by AM
  val CLINTSize = 0x00010000L
}