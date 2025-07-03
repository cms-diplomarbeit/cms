from encoder import load_encoder, encode_documents
from qdrant import connect_to_qdrant, create_collection, upload_documents, search
from generator import load_generator, build_prompt, generate_answer

# Schritt 1: Dokumente laden
def load_documents(path: str) -> list[str]:
    with open(path, "r", encoding="utf-8") as f:
        return [line.strip() for line in f if line.strip()]

def main():
    # 1. Dokumente laden
    documents = load_documents("data/documents.txt")

    # 2. Encoder vorbereiten (SBERT)
    encoder = load_encoder()
    embeddings = encode_documents(encoder, documents)

    # 3. Qdrant vorbereiten
    qdrant = connect_to_qdrant()
    create_collection(qdrant, "knowledge_base", vector_size=len(embeddings[0]))
    upload_documents(qdrant, "knowledge_base", documents, embeddings)

    # 4. Beispiel-Query
    frage = "Wie funktioniert RAG?"
    query_embedding = encode_documents(encoder, [frage])[0]

    # 5. Suche nach relevantem Kontext
    top_docs = search(qdrant, "knowledge_base", query_embedding, top_k=4)

    # 6. Generator vorbereiten (TinyLLaMA)
    generator = load_generator()
    prompt = build_prompt(top_docs, frage)
    antwort = generate_answer(generator, prompt)

    print("\nFrage:", frage)
    print("\nAntwort:\n", antwort)

    # 7. Antwort in Datei speichern
    with open("antwort.txt", "w", encoding="utf-8") as f:
        f.write(f"Frage: {frage}\n\nAntwort:\n{antwort}\n")
