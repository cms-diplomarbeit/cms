from sentence_transformers import SentenceTransformer


def load_encoder():
    """
    Loads our SBERT Encoder
    """
    model = SentenceTransformer("all-MiniLM-L6-v2")
    return model

def encode_documents(model, documents):
    """
    Encodes a list of documents using the provided model
    """
    return model.encode(documents, convert_to_tensor=False)

if __name__ == "__main__":
    docs = [
        "RAG kombiniert Retrieval mit Generierung.",
        "SBERT erzeugt semantische Embeddings.",
        "Qdrant ist eine Vektordatenbank."
    ]
    model = load_encoder()
    vectors = encode_documents(model, docs)

    for i, vec in enumerate(vectors):
        print(f"Vektor für Dokument {i}: {vec[:5]}... ({len(vec)} Dimensionen)")
