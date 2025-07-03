from qdrant_client import QdrantClient
from qdrant_client.http import models
import numpy as np

def connect_to_qdrant(host: str = "localhost", port: int = 6333) -> QdrantClient:
    """
    For the connection to the Qdrant database.
    """
    return QdrantClient(host=host, port=port)

def create_collection(client: QdrantClient, collection_name: str, vector_size: int = 384):
    """
    Creates a collection in Qdrant with the given Parameters.
    """
    client.recreate_collection(
        collection_name=collection_name,
        vectors_config=models.VectorParams(size=vector_size, distance=models.Distance.COSINE)
)
    
def upload_documents(client: QdrantClient, collection_name: str, documents: list[str], vectors: list[list[float]]):
    """
    Uploads documents and their corresponding vectors to the specified Qdrant collection.
    """

    payload = [{"text": doc} for doc in documents]
    ids = [i for i in range(len(documents))]

    client.upload_collection(
        collection_name=collection_name,
        vectors=np.array(vectors),
        payload=payload,
        ids=ids,
        batch_size=64,
        parallel=1
)

def search(client: QdrantClient, collection_name: str, query_vector: list[float], top_k: int = 3) -> list[str]:
    """
    Searches for the top_k most similar documents in the specified Qdrant collection
    based on the provided query vector.
    """
    results = client.search(
        collection_name=collection_name,
        query_vector=query_vector,
        limit=top_k
    )
    return [hit.payload["text"] for hit in results]