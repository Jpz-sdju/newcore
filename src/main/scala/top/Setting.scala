package top

trait Setting {
  // General Parameter for NutShell
  val XLEN =  64
  val HasCExtension = false
  val HasDiv = true
  val HasIcache = true
  val HasDcache = true
  val AddrBits = 64 // AddrBits is used in some cases
  // val VAddrBits = if (Settings.get("IsRV32")) 32 else 39 // VAddrBits is Virtual Memory addr bits
  val PAddrBits = 32 // PAddrBits is Phyical Memory addr bits
  val DataBits = XLEN
  val DataBytes = DataBits / 8
  val FetchBytes = 8



}