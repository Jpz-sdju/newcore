TLEdgeParameters(
    TLMasterPortParameters(
        List(
            TLMasterParameters(
                Iuncache, 
                IdRange(0,2), 
                List(), 
                List(AddressSet(0x0, ~0x0)),
                 Set(), false, false, P, TBALGFPH, 
                 false)
            ),TLChannelBeatBytes(None,None,None,None), 0, List(), List(), List()
        ),

    TLSlavePortParameters(List(
        TLSlaveParameters(
            l_soc, 
            List(AddressSet(0x80000000, 0x7fffffff)), 
            List(Resource(freechips.rocketchip.diplomacy.MemoryDevice@18da4dd,reg/mem)), 
            CACHED, true, None, BGH, PALGFPH, false, true, true)), 
            TLChannelBeatBytes(Some(32),Some(32),Some(32),Some(32)), 16, 2, List(), List(PrefetchKey, PreferCacheKey, AliasKey)),
    chipsalliance.rocketchip.config$ChainParameters@31a3f4de,SourceLine(Top.scala,82,50))

TLEdgeParameters(TLMasterPortParameters(List(TLMasterParameters(L1, IdRange(0,16), List(), List(AddressSet(0x0, ~0x0)), Set(), false, false, P, TBALGFPH, false)), TLChannelBeatBytes(Some(32),Some(32),Some(32),Some(32)), 1, List(), List(), List()),TLSlavePortParameters(List(TLSlaveParameters(l_soc, List(AddressSet(0x80000000, 0x7fffffff)), List(Resource(freechips.rocketchip.diplomacy.MemoryDevice@18da4dd,reg/mem)), UNCACHED, true, Some(0), GFP, PALGFPH, false, true, true)), TLChannelBeatBytes(Some(8),Some(8),Some(8),Some(8)), 0, 1, List(), List(AMBAProt)),chipsalliance.rocketchip.config$ChainParameters@31a3f4de,SourceLine(Top.scala,82,33))