# HybridChunkedBlockchain
Dissertation project on the topic of "Blockchain simulation and visualization". 
I have included the final dissertation for reference.

If you would like to use any code / dissertation text, please do mind the licence and do cite properly. I would be more than happy to know of any projects/ideas that are built on top of (or inspired by) my dissertation, so feel free to e-mail me.

The simulation is tick-based, and essentially executes event queue. That allows it to simulate a larger amount of nodes on one machine. It uses a federated model of operation, which maintains two blockchains: Data and Access. Only nodes with access specified in the Access blockchain can change the Data blockchain. This mode of usage is aimed towards such applications, where a singular entity wants to leverage blockchain redundancy and stability, whilst maintaining a degree of control over network structure and Data blockchain contents. One of desired applications is e-government systems, with an example case of "EET" (Czech: "Elektronická Evidence Tržeb", English: "Electronic Registration of Sales").

In short, I have suggested a use of "chunked blockchain" within this simulation, which allows for a fast-forward fetching of blocks for devices with limited space. This approach is very useful for blockchain applications which do not have the need to fully validate blockchain data, and that have block storage redundancies without full use of main blockchain in their logic; this logic must also contain block querying abilities.
The chunked blockchain is essentially a wrapper over the hashchain of the primary blockchain, and thus allows devices to not use main hash chain, but to combine the two to establish a valid hash chain. 
