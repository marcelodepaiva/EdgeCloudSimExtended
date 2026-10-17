# Script para gerar as 41 escolas no padrão correto
with open("edge_devices2.xml", "w") as f:
    f.write('<?xml version="1.0"?>\n<edge_devices>\n')
    for i in range(41):
        f.write(f'''    <datacenter arch="x86" os="Linux" vmm="Xen">
        <costPerBw>0.1</costPerBw><costPerSec>3.0</costPerSec><costPerMem>0.05</costPerMem><costPerStorage>0.1</costPerStorage>
        <location><x_pos>{(i+1)*10}</x_pos><y_pos>{(i+1)*10}</y_pos><wlan_id>{i}</wlan_id><attractiveness>{i%3}</attractiveness></location>
        <hosts><host><core>4</core><mips>80000</mips><ram>16000</ram><storage>400000</storage>
        <VMs>
            {''.join(['<VM vmm="Xen"><core>2</core><mips>10000</mips><ram>2000</ram><storage>50000</storage></VM>' for _ in range(8)])}
        </VMs></host></hosts>
    </datacenter>\n''')
    f.write('</edge_devices>')
print("Arquivo edge_devices.xml gerado com sucesso!")
